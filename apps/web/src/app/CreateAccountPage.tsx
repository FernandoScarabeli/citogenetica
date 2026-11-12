import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ArrowRight } from 'lucide-react';
import { Link, Navigate } from 'react-router';
import { ApiError, NetworkError } from '../api/errors';
import { authApi } from '../api/auth-api';
import type { UsuarioRequestDTO } from '../api/contracts/auth';
import { useSession } from './SessionProvider';
import { AuthLayout } from './AuthLayout';
import {
  validateRegistration,
  validateResendEmail,
  type RegistrationErrors,
  type RegistrationField,
  type RegistrationValues,
} from './registration-validation';
import { retryCountdownMessage, useRetryCountdown } from './useRetryCountdown';

type View = 'form' | 'email-sent';
type RateLimitedAction = 'register' | 'resend' | null;

const registrationFieldOrder: RegistrationField[] = ['nome', 'email', 'senha', 'confirmacaoSenha'];

function firstMessage(messages: string[] | undefined): string | undefined {
  return messages?.find((message) => message.trim().length > 0);
}

function mapRegistrationFieldErrors(error: ApiError): RegistrationErrors {
  const fieldErrors: RegistrationErrors = {};
  for (const field of registrationFieldOrder) {
    const message = firstMessage(error.fieldErrors[field]);
    if (message) fieldErrors[field] = message;
  }

  if (error.status === 400 && Object.keys(fieldErrors).length === 0
    && /senha|password|bcrypt|vazamento|comum/i.test(error.message)) {
    fieldErrors.senha = error.message;
  }
  return fieldErrors;
}

export function CreateAccountPage() {
  const { status, retry } = useSession();
  const [view, setView] = useState<View>('form');
  const [values, setValues] = useState<RegistrationValues>({ nome: '', email: '', senha: '', confirmacaoSenha: '' });
  const [fieldErrors, setFieldErrors] = useState<RegistrationErrors>({});
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [resending, setResending] = useState(false);
  const [resendNotice, setResendNotice] = useState(false);
  const [rateLimitedAction, setRateLimitedAction] = useState<RateLimitedAction>(null);
  const countdown = useRetryCountdown();
  const titleRef = useRef<HTMLHeadingElement>(null);
  const globalErrorRef = useRef<HTMLDivElement>(null);
  const focusInvalidRef = useRef(false);
  const focusGlobalErrorRef = useRef(false);
  const fields = useRef<Partial<Record<RegistrationField, HTMLInputElement | null>>>({});

  useEffect(() => {
    if (view === 'email-sent') titleRef.current?.focus();
  }, [view]);

  useEffect(() => {
    if (!focusInvalidRef.current) return;
    focusInvalidRef.current = false;
    const firstInvalidField = registrationFieldOrder.find((field) => fieldErrors[field]);
    if (firstInvalidField) fields.current[firstInvalidField]?.focus();
  }, [fieldErrors]);

  useEffect(() => {
    if (!focusGlobalErrorRef.current) return;
    focusGlobalErrorRef.current = false;
    globalErrorRef.current?.focus();
  }, [globalError]);

  if (status === 'authenticated') return <Navigate to="/laboratorios" replace/>;
  if (status === 'loading') {
    return <main className="session-state" aria-live="polite"><p>Verificando sua sessão…</p></main>;
  }
  if (status === 'error') {
    return <main className="session-state" aria-labelledby="signup-session-error-title">
      <h1 id="signup-session-error-title">Não foi possível verificar sua sessão</h1>
      <p>Verifique sua conexão e tente novamente.</p>
      <button className="button primary" type="button" onClick={() => void retry()}>Tentar de novo</button>
    </main>;
  }

  function changeField(field: RegistrationField, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => {
      const next = { ...current };
      delete next[field];
      if (field === 'senha') delete next.confirmacaoSenha;
      return next;
    });
    setGlobalError(null);
  }

  function showFieldErrors(errors: RegistrationErrors) {
    focusInvalidRef.current = true;
    setFieldErrors(errors);
  }

  function showGlobalError(message: string) {
    focusGlobalErrorRef.current = true;
    setGlobalError(message);
  }

  function showRateLimit(action: Exclude<RateLimitedAction, null>, error: ApiError) {
    setRateLimitedAction(action);
    countdown.start(error.retryAfterSeconds);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || (countdown.isWaiting && rateLimitedAction === 'register')) return;

    const errors = validateRegistration(values);
    if (Object.keys(errors).length > 0) {
      showFieldErrors(errors);
      return;
    }

    setSubmitting(true);
    setGlobalError(null);
    setFieldErrors({});
    setRateLimitedAction(null);
    countdown.clear();
    try {
      const request: UsuarioRequestDTO = {
        nome: values.nome,
        email: values.email.trim(),
        senha: values.senha,
      };
      await authApi.register(request);
      setView('email-sent');
    } catch (error) {
      if (error instanceof ApiError && error.status === 429) {
        showRateLimit('register', error);
      } else if (error instanceof ApiError) {
        const serverFieldErrors = mapRegistrationFieldErrors(error);
        if (Object.keys(serverFieldErrors).length > 0) {
          showFieldErrors(serverFieldErrors);
        } else {
          showGlobalError(error.message || 'Não foi possível criar a conta. Tente novamente.');
        }
      } else if (error instanceof NetworkError) {
        showGlobalError('Não foi possível conectar à API. Verifique sua conexão e tente novamente.');
      } else {
        showGlobalError('Não foi possível criar a conta. Tente novamente.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function resendEmail() {
    if (resending || (countdown.isWaiting && rateLimitedAction === 'resend')) return;
    const emailError = validateResendEmail(values.email);
    if (emailError) {
      showFieldErrors({ email: emailError });
      return;
    }

    setResending(true);
    setResendNotice(false);
    setGlobalError(null);
    setFieldErrors({});
    setRateLimitedAction(null);
    countdown.clear();
    try {
      await authApi.resendEmailConfirmation(values.email.trim());
      setResendNotice(true);
    } catch (error) {
      if (error instanceof ApiError && error.status === 429) {
        showRateLimit('resend', error);
      } else if (error instanceof ApiError) {
        const emailMessage = firstMessage(error.fieldErrors.email);
        if (emailMessage) showFieldErrors({ email: emailMessage });
        else showGlobalError(error.message || 'Não foi possível reenviar o e-mail. Tente novamente.');
      } else if (error instanceof NetworkError) {
        showGlobalError('Não foi possível conectar à API. Verifique sua conexão e tente novamente.');
      } else {
        showGlobalError('Não foi possível reenviar o e-mail. Tente novamente.');
      }
    } finally {
      setResending(false);
    }
  }

  const currentRateAction = view === 'email-sent' ? 'resend' : 'register';
  const rateLimitVisible = rateLimitedAction === currentRateAction;
  const rateLimitMessage = countdown.isWaiting
    ? `Muitas solicitações. ${retryCountdownMessage(countdown.remainingSeconds ?? 0)}`
    : countdown.remainingSeconds === 0
      ? 'Você já pode tentar novamente.'
      : 'Muitas solicitações. Tente novamente mais tarde.';

  if (view === 'email-sent') {
    return <AuthLayout
      title="Confira seu e-mail"
      titleId="signup-result-title"
      description="Se houver uma confirmação de cadastro pendente para esse endereço, enviaremos uma mensagem com as próximas instruções."
      titleRef={titleRef}
      titleFocusable
    >
      {resendNotice && <p className="login-notice" role="status">Se houver uma confirmação pendente para esse endereço, um novo e-mail chegará em instantes.</p>}
      {globalError && <div ref={globalErrorRef} className="login-error" role="alert" tabIndex={-1}>{globalError}</div>}
      {rateLimitVisible && <p className={countdown.remainingSeconds === 0 ? 'login-notice' : 'login-error'} role={countdown.remainingSeconds === 0 ? 'status' : 'alert'}>
        {rateLimitMessage}
      </p>}
      <button className="access-signin" type="button" onClick={() => void resendEmail()} disabled={resending || (countdown.isWaiting && rateLimitedAction === 'resend')}>
        {resending ? 'Enviando…' : 'Reenviar e-mail'}
        {!resending && <ArrowRight size={17} aria-hidden="true"/>}
      </button>
      <Link className="auth-secondary-link" to="/entrar">Ir para entrar</Link>
    </AuthLayout>;
  }

  return <AuthLayout
    title="Crie sua conta."
    titleId="signup-title"
    description="Informe seus dados para solicitar o cadastro."
  >
    {globalError && <div ref={globalErrorRef} className="login-error" role="alert" tabIndex={-1}>{globalError}</div>}
    {rateLimitVisible && <p className={countdown.remainingSeconds === 0 ? 'login-notice' : 'login-error'} role={countdown.remainingSeconds === 0 ? 'status' : 'alert'}>
      {rateLimitMessage}
    </p>}
    <form className="access-form" onSubmit={submit} noValidate>
      <div className="access-field">
        <label htmlFor="signup-name">Nome</label>
        <input
          ref={(element) => { fields.current.nome = element; }}
          id="signup-name"
          name="nome"
          type="text"
          autoComplete="name"
          required
          maxLength={100}
          value={values.nome}
          aria-invalid={fieldErrors.nome ? true : undefined}
          aria-describedby={fieldErrors.nome ? 'signup-name-error' : undefined}
          onChange={(event) => changeField('nome', event.target.value)}
        />
        {fieldErrors.nome && <p id="signup-name-error" className="access-field-error" role="alert">{fieldErrors.nome}</p>}
      </div>
      <div className="access-field">
        <label htmlFor="signup-email">E-mail</label>
        <input
          ref={(element) => { fields.current.email = element; }}
          id="signup-email"
          name="email"
          type="email"
          autoComplete="email"
          required
          value={values.email}
          aria-invalid={fieldErrors.email ? true : undefined}
          aria-describedby={fieldErrors.email ? 'signup-email-error' : undefined}
          onChange={(event) => changeField('email', event.target.value)}
        />
        {fieldErrors.email && <p id="signup-email-error" className="access-field-error" role="alert">{fieldErrors.email}</p>}
      </div>
      <div className="access-field">
        <label htmlFor="signup-password">Senha</label>
        <div className="password-input-wrap">
          <input
            ref={(element) => { fields.current.senha = element; }}
            id="signup-password"
            name="senha"
            type={showPassword ? 'text' : 'password'}
            autoComplete="new-password"
            required
            value={values.senha}
            aria-invalid={fieldErrors.senha ? true : undefined}
            aria-describedby={`signup-password-help${fieldErrors.senha ? ' signup-password-error' : ''}`}
            onChange={(event) => changeField('senha', event.target.value)}
          />
          <button
            className="access-password-toggle"
            type="button"
            aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
            aria-pressed={showPassword}
            aria-controls="signup-password"
            onClick={() => setShowPassword((visible) => !visible)}
          >{showPassword ? 'Ocultar' : 'Mostrar'}</button>
        </div>
        <p id="signup-password-help" className="access-field-help">Use de 8 a 64 caracteres e até 72 bytes em UTF-8. Senhas comuns ou conhecidas em vazamentos são recusadas.</p>
        {fieldErrors.senha && <p id="signup-password-error" className="access-field-error" role="alert">{fieldErrors.senha}</p>}
      </div>
      <div className="access-field">
        <label htmlFor="signup-password-confirmation">Confirme sua senha</label>
        <input
          ref={(element) => { fields.current.confirmacaoSenha = element; }}
          id="signup-password-confirmation"
          name="confirmacaoSenha"
          type="password"
          autoComplete="new-password"
          required
          value={values.confirmacaoSenha}
          aria-invalid={fieldErrors.confirmacaoSenha ? true : undefined}
          aria-describedby={fieldErrors.confirmacaoSenha ? 'signup-confirmation-error' : undefined}
          onChange={(event) => changeField('confirmacaoSenha', event.target.value)}
        />
        {fieldErrors.confirmacaoSenha && <p id="signup-confirmation-error" className="access-field-error" role="alert">{fieldErrors.confirmacaoSenha}</p>}
      </div>
      <button className="access-signin" type="submit" disabled={submitting || (countdown.isWaiting && rateLimitedAction === 'register')}>
        {submitting ? 'Enviando…' : 'Criar conta'}
        {!submitting && <ArrowRight size={17} aria-hidden="true"/>}
      </button>
    </form>
    <p className="auth-switch">Já tem uma conta? <Link to="/entrar">Entrar</Link></p>
  </AuthLayout>;
}
