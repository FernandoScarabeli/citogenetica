import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router';
import { authApi } from '../api/auth-api';
import { ApiError, NetworkError } from '../api/errors';
import type { RedefinirSenhaRequestDTO } from '../api/contracts/auth';
import { AuthLayout } from './AuthLayout';
import { passwordPolicyError, validateResendEmail } from './registration-validation';
import { retryCountdownMessage, useRetryCountdown } from './useRetryCountdown';

type RecoveryStep = 'email' | 'code' | 'password' | 'success';
type RecoveryAction = 'request' | 'validate' | 'resend' | 'reset';
type RecoveryField = 'email' | 'code' | 'password' | 'confirmation';
type RecoveryErrors = Partial<Record<RecoveryField, string>>;

interface RecoveryValues {
  email: string;
  code: string;
  password: string;
  confirmation: string;
}

const initialValues: RecoveryValues = { email: '', code: '', password: '', confirmation: '' };
const challengeError = 'Código inválido ou expirado. Confira os seis dígitos ou solicite um novo código.';

function digitsOnly(value: string): string {
  return value.replace(/[^0-9]/g, '').slice(0, 6);
}

function rateLimitMessage(action: RecoveryAction, remainingSeconds: number | null, isWaiting: boolean): string {
  const noun = action === 'request' || action === 'resend' ? 'solicitações' : 'tentativas';
  if (isWaiting) return `Muitas ${noun}. ${retryCountdownMessage(remainingSeconds ?? 0)}`;
  if (remainingSeconds === 0) return 'Você já pode tentar novamente.';
  return `Muitas ${noun}. Tente novamente mais tarde.`;
}

export function RecoverPasswordPage() {
  const [step, setStep] = useState<RecoveryStep>('email');
  const [values, setValues] = useState<RecoveryValues>(initialValues);
  const [errors, setErrors] = useState<RecoveryErrors>({});
  const [notice, setNotice] = useState<string | null>(null);
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [rateLimitedAction, setRateLimitedAction] = useState<RecoveryAction | null>(null);
  const countdown = useRetryCountdown();
  const titleRef = useRef<HTMLHeadingElement>(null);
  const previousStep = useRef(step);
  const emailRef = useRef<HTMLInputElement>(null);
  const codeRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const confirmationRef = useRef<HTMLInputElement>(null);
  const globalErrorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (previousStep.current !== step) {
      titleRef.current?.focus();
      previousStep.current = step;
    }
  }, [step]);

  useEffect(() => {
    if (globalError) globalErrorRef.current?.focus();
  }, [globalError]);

  function setField(field: RecoveryField, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => {
      const next = { ...current };
      delete next[field];
      return next;
    });
    setGlobalError(null);
  }

  function restartFlow() {
    setStep('email');
    setValues(initialValues);
    setErrors({});
    setNotice(null);
    setGlobalError(null);
    setRateLimitedAction(null);
    countdown.clear();
  }

  function goBack() {
    setGlobalError(null);
    setNotice(null);
    if (step === 'code') {
      setStep('email');
      setValues((current) => ({ ...current, code: '' }));
      setErrors({});
    } else if (step === 'password') {
      setStep('code');
      setValues((current) => ({ ...current, password: '', confirmation: '' }));
      setErrors({});
    }
  }

  function showRateLimit(action: RecoveryAction, error: ApiError) {
    setRateLimitedAction(action);
    countdown.start(error.retryAfterSeconds);
  }

  async function requestCode(action: 'request' | 'resend') {
    if (busy || (countdown.isWaiting && rateLimitedAction === action)) return;
    setBusy(true);
    setNotice(null);
    setGlobalError(null);
    try {
      await authApi.requestPasswordRecovery({ email: values.email.trim() });
      setValues((current) => ({ ...current, email: current.email.trim(), code: '' }));
      setErrors({});
      setNotice('Se houver uma conta associada a esse endereço, enviaremos um código de 6 dígitos.');
      if (rateLimitedAction === action) {
        setRateLimitedAction(null);
        countdown.clear();
      }
      setStep('code');
    } catch (error) {
      if (error instanceof ApiError && error.status === 429) {
        showRateLimit(action, error);
      } else if (error instanceof NetworkError) {
        setGlobalError('Não foi possível conectar à API. Verifique sua conexão e tente novamente.');
      } else {
        setGlobalError('Não foi possível solicitar o código. Tente novamente.');
      }
    } finally {
      setBusy(false);
    }
  }

  async function submitEmail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const emailError = validateResendEmail(values.email);
    if (emailError) {
      setErrors({ email: emailError });
      emailRef.current?.focus();
      return;
    }
    await requestCode('request');
  }

  async function submitCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!/^\d{6}$/.test(values.code)) {
      setErrors({ code: 'Informe os seis dígitos do código.' });
      codeRef.current?.focus();
      return;
    }
    if (busy || (countdown.isWaiting && rateLimitedAction === 'validate')) return;

    setBusy(true);
    setNotice(null);
    setGlobalError(null);
    setErrors({});
    try {
      await authApi.validateRecoveryCode({ email: values.email, codigo: values.code });
      if (rateLimitedAction === 'validate') {
        setRateLimitedAction(null);
        countdown.clear();
      }
      setStep('password');
    } catch (error) {
      if (error instanceof ApiError && error.status === 429) {
        showRateLimit('validate', error);
      } else if (error instanceof ApiError && error.status === 400) {
        setErrors({ code: challengeError });
        codeRef.current?.focus();
      } else if (error instanceof NetworkError) {
        setGlobalError('Não foi possível conectar à API. Verifique sua conexão e tente novamente.');
      } else {
        setGlobalError('Não foi possível validar o código. Tente novamente.');
      }
    } finally {
      setBusy(false);
    }
  }

  async function submitPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors: RecoveryErrors = {};
    const passwordError = passwordPolicyError(values.password);
    if (passwordError) nextErrors.password = passwordError;
    if (!values.confirmation) nextErrors.confirmation = 'Confirme sua nova senha.';
    else if (values.password !== values.confirmation) nextErrors.confirmation = 'As senhas não coincidem.';
    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      if (nextErrors.password) passwordRef.current?.focus();
      else confirmationRef.current?.focus();
      return;
    }
    if (busy || (countdown.isWaiting && rateLimitedAction === 'reset')) return;

    setBusy(true);
    setNotice(null);
    setGlobalError(null);
    setErrors({});
    const request: RedefinirSenhaRequestDTO = {
      email: values.email,
      codigo: values.code,
      novaSenha: values.password,
    };
    try {
      await authApi.resetPassword(request);
      if (rateLimitedAction === 'reset') {
        setRateLimitedAction(null);
        countdown.clear();
      }
      setValues(initialValues);
      setStep('success');
    } catch (error) {
      if (error instanceof ApiError && error.status === 429) {
        showRateLimit('reset', error);
      } else if (error instanceof ApiError && error.message === 'Código inválido ou expirado.') {
        setValues((current) => ({ ...current, password: '', confirmation: '' }));
        setStep('code');
        setErrors({ code: challengeError });
        codeRef.current?.focus();
      } else if (error instanceof ApiError && (error.status === 400 || error.status === 409)
        && /senha|password|bcrypt|vazamento|comum|atual/i.test(error.message)) {
        setErrors({ password: error.message });
        passwordRef.current?.focus();
      } else if (error instanceof NetworkError) {
        setGlobalError('Não foi possível conectar à API. Verifique sua conexão e tente novamente.');
      } else {
        setGlobalError('Não foi possível redefinir a senha. Tente novamente.');
      }
    } finally {
      setBusy(false);
    }
  }

  const rateLimitVisible = rateLimitedAction !== null && (
    (step === 'email' && rateLimitedAction === 'request')
    || (step === 'code' && (rateLimitedAction === 'validate' || rateLimitedAction === 'resend'))
    || (step === 'password' && rateLimitedAction === 'reset')
  );
  const rateMessage = rateLimitVisible && rateLimitedAction
    ? rateLimitMessage(rateLimitedAction, countdown.remainingSeconds, countdown.isWaiting)
    : null;
  const errorDescription = globalError ? 'recovery-global-error' : undefined;

  if (step === 'success') {
    return <AuthLayout
      title="Senha redefinida"
      titleId="recovery-success-title"
      titleRef={titleRef}
      titleFocusable
      description="Sua senha foi alterada com sucesso. Por segurança, as sessões abertas desta conta foram encerradas. Entre novamente com a nova senha."
    >
      <Link className="access-signin" to="/entrar">Ir para entrar <ArrowRight size={17} aria-hidden="true"/></Link>
    </AuthLayout>;
  }

  const titles: Record<Exclude<RecoveryStep, 'success'>, string> = {
    email: 'Recuperar senha',
    code: 'Confira seu e-mail',
    password: 'Defina uma nova senha',
  };
  const descriptions: Record<Exclude<RecoveryStep, 'success'>, string> = {
    email: 'Informe o e-mail da sua conta para receber um código. Se atualizar esta página, o processo recomeça.',
    code: 'Digite abaixo o código de 6 dígitos. Se houver uma conta para esse endereço, enviaremos as instruções por e-mail.',
    password: 'Escolha uma senha nova para acessar seu espaço de pesquisa.',
  };

  return <AuthLayout
    title={titles[step]}
    titleId="recovery-title"
    titleRef={titleRef}
    titleFocusable
    description={descriptions[step]}
  >
    <p className="recovery-step">Etapa {step === 'email' ? '1' : step === 'code' ? '2' : '3'} de 3</p>
    {notice && <p className="login-notice" role="status">{notice}</p>}
    {globalError && <div ref={globalErrorRef} id="recovery-global-error" className="login-error" role="alert" tabIndex={-1}>{globalError}</div>}
    {rateMessage && <p className={countdown.remainingSeconds === 0 ? 'login-notice' : 'login-error'} role={countdown.remainingSeconds === 0 ? 'status' : 'alert'}>
      {rateMessage}
    </p>}

    {step === 'email' && <>
      <form className="access-form" method="post" autoComplete="off" noValidate onSubmit={(event) => void submitEmail(event)}>
        <div className="access-field">
          <label htmlFor="recovery-email">E-mail</label>
          <input
            ref={emailRef}
            id="recovery-email"
            name="email"
            type="email"
            autoComplete="off"
            required
            maxLength={255}
            value={values.email}
            aria-invalid={errors.email ? true : undefined}
            aria-describedby={errors.email ? 'recovery-email-error' : errorDescription}
            onChange={(event) => setField('email', event.target.value)}
          />
          {errors.email && <p id="recovery-email-error" className="access-field-error" role="alert">{errors.email}</p>}
        </div>
        <button className="access-signin" type="submit" disabled={busy || (countdown.isWaiting && rateLimitedAction === 'request')}>
          {busy ? 'Enviando…' : 'Enviar código'} <ArrowRight size={17} aria-hidden="true"/>
        </button>
      </form>
      <div className="recovery-actions">
        <Link className="auth-secondary-link" to="/entrar">Voltar para entrar</Link>
      </div>
    </>}

    {step === 'code' && <>
      <form className="access-form" method="post" noValidate onSubmit={(event) => void submitCode(event)}>
        <div className="access-field">
          <label htmlFor="recovery-code">Código de 6 dígitos</label>
          <input
            ref={codeRef}
            className="recovery-code-input"
            id="recovery-code"
            name="codigo"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="[0-9]{6}"
            maxLength={6}
            required
            value={values.code}
            aria-invalid={errors.code ? true : undefined}
            aria-describedby={errors.code ? 'recovery-code-error' : errorDescription}
            onChange={(event) => setField('code', digitsOnly(event.target.value))}
            onPaste={(event) => {
              event.preventDefault();
              setField('code', digitsOnly(event.clipboardData.getData('text')));
            }}
          />
          {errors.code && <p id="recovery-code-error" className="access-field-error" role="alert">{errors.code}</p>}
        </div>
        <button className="access-signin" type="submit" disabled={busy || (countdown.isWaiting && rateLimitedAction === 'validate')}>
          {busy ? 'Verificando…' : 'Validar código'} <ArrowRight size={17} aria-hidden="true"/>
        </button>
      </form>
      <div className="recovery-actions">
        <button className="auth-secondary-button" type="button" onClick={() => void requestCode('resend')} disabled={busy || (countdown.isWaiting && rateLimitedAction === 'resend')}>Reenviar código</button>
        <button className="auth-secondary-button" type="button" onClick={goBack} disabled={busy}>Voltar para o e-mail</button>
        <button className="auth-secondary-button" type="button" onClick={restartFlow} disabled={busy}>Usar outro e-mail</button>
      </div>
    </>}

    {step === 'password' && <>
      <form className="access-form" method="post" noValidate onSubmit={(event) => void submitPassword(event)}>
        <div className="access-field">
          <label htmlFor="recovery-password">Nova senha</label>
          <input
            ref={passwordRef}
            id="recovery-password"
            name="novaSenha"
            type="password"
            autoComplete="new-password"
            required
            value={values.password}
            aria-invalid={errors.password ? true : undefined}
            aria-describedby={`recovery-password-help${errors.password ? ' recovery-password-error' : ''}`}
            onChange={(event) => setField('password', event.target.value)}
          />
          <p id="recovery-password-help" className="access-field-help">Use de 8 a 64 caracteres e até 72 bytes em UTF-8. Senhas comuns ou conhecidas em vazamentos são recusadas.</p>
          {errors.password && <p id="recovery-password-error" className="access-field-error" role="alert">{errors.password}</p>}
        </div>
        <div className="access-field">
          <label htmlFor="recovery-confirmation">Confirme sua nova senha</label>
          <input
            ref={confirmationRef}
            id="recovery-confirmation"
            name="confirmacaoSenha"
            type="password"
            autoComplete="new-password"
            required
            value={values.confirmation}
            aria-invalid={errors.confirmation ? true : undefined}
            aria-describedby={errors.confirmation ? 'recovery-confirmation-error' : undefined}
            onChange={(event) => setField('confirmation', event.target.value)}
          />
          {errors.confirmation && <p id="recovery-confirmation-error" className="access-field-error" role="alert">{errors.confirmation}</p>}
        </div>
        <button className="access-signin" type="submit" disabled={busy || (countdown.isWaiting && rateLimitedAction === 'reset')}>
          {busy ? 'Salvando…' : 'Redefinir senha'} <ArrowRight size={17} aria-hidden="true"/>
        </button>
      </form>
      <div className="recovery-actions">
        <button className="auth-secondary-button" type="button" onClick={goBack} disabled={busy}>Voltar para o código</button>
        <button className="auth-secondary-button" type="button" onClick={restartFlow} disabled={busy}>Usar outro e-mail</button>
      </div>
    </>}
  </AuthLayout>;
}
