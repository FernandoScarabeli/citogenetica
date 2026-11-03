import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router';
import { ApiError, NetworkError } from '../api/errors';
import { authApi } from '../api/auth-api';
import { AuthLayout } from './AuthLayout';
import { takeConfirmationToken } from './confirmation-token';
import { validateResendEmail } from './registration-validation';
import { retryCountdownMessage, useRetryCountdown } from './useRetryCountdown';

type ConfirmationStatus = 'checking' | 'confirmed' | 'invalid' | 'unavailable';

export function ConfirmEmailPage() {
  const [status, setStatus] = useState<ConfirmationStatus>('checking');
  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState<string | null>(null);
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [resendNotice, setResendNotice] = useState(false);
  const [resending, setResending] = useState(false);
  const [rateLimited, setRateLimited] = useState(false);
  const [rateLimitWithoutRetryAfter, setRateLimitWithoutRetryAfter] = useState(false);
  const countdown = useRetryCountdown();
  const startCountdown = countdown.start;
  const startedRef = useRef(false);
  const titleRef = useRef<HTMLHeadingElement>(null);
  const emailRef = useRef<HTMLInputElement>(null);
  const globalErrorRef = useRef<HTMLDivElement>(null);
  const focusEmailRef = useRef(false);
  const focusGlobalErrorRef = useRef(false);

  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;

    const token = takeConfirmationToken();

    if (!token) {
      setStatus('invalid');
      return;
    }

    void authApi.confirmEmail(token).then(() => {
      setStatus('confirmed');
    }).catch((error: unknown) => {
      if (error instanceof ApiError && error.status === 400) {
        setStatus('invalid');
      } else if (error instanceof ApiError && error.status === 429) {
        setStatus('unavailable');
        setRateLimited(true);
        setRateLimitWithoutRetryAfter(!(error.retryAfterSeconds && error.retryAfterSeconds > 0));
        startCountdown(error.retryAfterSeconds);
      } else if (error instanceof NetworkError) {
        setGlobalError('Não foi possível conectar à API. Abra novamente o link recebido para tentar mais tarde.');
        setStatus('unavailable');
      } else {
        setGlobalError('Não foi possível confirmar o e-mail agora. Abra novamente o link recebido para tentar mais tarde.');
        setStatus('unavailable');
      }
    });
  }, [startCountdown]);

  useEffect(() => {
    if (status !== 'checking') titleRef.current?.focus();
  }, [status]);

  useEffect(() => {
    if (!focusEmailRef.current) return;
    focusEmailRef.current = false;
    emailRef.current?.focus();
  }, [emailError]);

  useEffect(() => {
    if (!focusGlobalErrorRef.current) return;
    focusGlobalErrorRef.current = false;
    globalErrorRef.current?.focus();
  }, [globalError]);

  async function resendEmail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (resending || countdown.isWaiting) return;

    const validationMessage = validateResendEmail(email);
    if (validationMessage) {
      focusEmailRef.current = true;
      setEmailError(validationMessage);
      return;
    }

    setResending(true);
    setEmailError(null);
    setGlobalError(null);
    setResendNotice(false);
    setRateLimited(false);
    setRateLimitWithoutRetryAfter(false);
    countdown.clear();
    try {
      await authApi.resendEmailConfirmation(email.trim());
      setResendNotice(true);
    } catch (error) {
      if (error instanceof ApiError && error.status === 429) {
        setRateLimited(true);
        setRateLimitWithoutRetryAfter(!(error.retryAfterSeconds && error.retryAfterSeconds > 0));
        startCountdown(error.retryAfterSeconds);
      } else if (error instanceof ApiError) {
        const serverEmailError = error.fieldErrors.email?.[0];
        if (serverEmailError) {
          focusEmailRef.current = true;
          setEmailError(serverEmailError);
        } else {
          focusGlobalErrorRef.current = true;
          setGlobalError(error.message || 'Não foi possível reenviar o e-mail. Tente novamente.');
        }
      } else if (error instanceof NetworkError) {
        focusGlobalErrorRef.current = true;
        setGlobalError('Não foi possível conectar à API. Verifique sua conexão e tente novamente.');
      } else {
        focusGlobalErrorRef.current = true;
        setGlobalError('Não foi possível reenviar o e-mail. Tente novamente.');
      }
    } finally {
      setResending(false);
    }
  }

  const title = status === 'checking'
    ? 'Confirmando seu e-mail…'
    : status === 'confirmed'
      ? 'E-mail confirmado'
      : status === 'invalid'
        ? 'Link inválido ou expirado'
        : 'Não foi possível confirmar agora';
  const description = status === 'checking'
    ? 'Estamos verificando o link de confirmação.'
    : status === 'confirmed'
      ? 'Seu endereço de e-mail foi confirmado. Agora você pode entrar.'
      : status === 'invalid'
        ? 'O link de confirmação é inválido ou expirou. Informe seu e-mail para receber um novo link.'
        : 'Abra novamente o link recebido para tentar mais tarde.';
  const retryMessage = countdown.isWaiting
    ? `Muitas solicitações. ${retryCountdownMessage(countdown.remainingSeconds ?? 0)} Abra novamente o link depois desse prazo.`
    : countdown.remainingSeconds === 0
      ? 'O prazo de espera terminou. Abra novamente o link recebido para tentar mais uma vez.'
      : 'Muitas solicitações. Tente novamente mais tarde.';

  return <AuthLayout
    title={title}
    titleId="confirm-email-title"
    description={description}
    titleRef={titleRef}
    titleFocusable={status !== 'checking'}
  >
    {status === 'checking' && <p className="login-notice" role="status">Verificando o link…</p>}
    {status === 'confirmed' && <Link className="access-signin" to="/entrar">Entrar <ArrowRight size={17} aria-hidden="true"/></Link>}
    {status === 'invalid' && <>
      {globalError && <div ref={globalErrorRef} className="login-error" role="alert" tabIndex={-1}>{globalError}</div>}
      {resendNotice && <p className="login-notice" role="status">Se houver uma confirmação pendente para esse endereço, um novo e-mail chegará em instantes.</p>}
      {(rateLimited || (emailError && status === 'invalid')) && <p className={countdown.remainingSeconds === 0 ? 'login-notice' : 'login-error'} role={countdown.remainingSeconds === 0 ? 'status' : 'alert'}>
        {rateLimited
          ? (rateLimitWithoutRetryAfter ? 'Muitas solicitações. Tente novamente mais tarde.' : retryMessage)
          : emailError}
      </p>}
      <form className="access-form" onSubmit={resendEmail} noValidate>
        <div className="access-field">
          <label htmlFor="confirm-resend-email">E-mail</label>
          <input
            ref={emailRef}
            id="confirm-resend-email"
            name="email"
            type="email"
            autoComplete="email"
            required
            maxLength={255}
            value={email}
            aria-invalid={emailError ? true : undefined}
            aria-describedby={emailError ? 'confirm-resend-email-error' : undefined}
            onChange={(event) => { setEmail(event.target.value); setEmailError(null); }}
          />
          {emailError && <p id="confirm-resend-email-error" className="access-field-error" role="alert">{emailError}</p>}
        </div>
        <button className="access-signin" type="submit" disabled={resending || countdown.isWaiting}>
          {resending ? 'Enviando…' : 'Reenviar e-mail'}
          {!resending && <ArrowRight size={17} aria-hidden="true"/>}
        </button>
      </form>
      <Link className="auth-secondary-link" to="/entrar">Ir para entrar</Link>
    </>}
    {status === 'unavailable' && <>
      {globalError && <div ref={globalErrorRef} className="login-error" role="alert" tabIndex={-1}>{globalError}</div>}
      {rateLimited && <p className={countdown.remainingSeconds === 0 ? 'login-notice' : 'login-error'} role={countdown.remainingSeconds === 0 ? 'status' : 'alert'}>
        {rateLimitWithoutRetryAfter ? 'Muitas solicitações. Tente novamente mais tarde.' : retryMessage}
      </p>}
      <Link className="auth-secondary-link" to="/entrar">Ir para entrar</Link>
    </>}
  </AuthLayout>;
}
