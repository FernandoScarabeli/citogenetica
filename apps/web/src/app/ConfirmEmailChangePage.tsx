import { useEffect, useRef, useState } from 'react';
import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router';
import { ApiError, NetworkError } from '../api/errors';
import { authApi } from '../api/auth-api';
import { AuthLayout } from './AuthLayout';
import { takeConfirmationToken } from './confirmation-token';
import { useSession } from './SessionProvider';
import { retryCountdownMessage, useRetryCountdown } from './useRetryCountdown';

type ConfirmationStatus = 'checking' | 'changed' | 'invalid' | 'unavailable';

export function ConfirmEmailChangePage() {
  const [status, setStatus] = useState<ConfirmationStatus>('checking');
  const [rateLimited, setRateLimited] = useState(false);
  const [rateLimitWithoutRetryAfter, setRateLimitWithoutRetryAfter] = useState(false);
  const [globalError, setGlobalError] = useState<string | null>(null);
  const { clearSession } = useSession();
  const countdown = useRetryCountdown();
  const startCountdown = countdown.start;
  const startedRef = useRef(false);
  const titleRef = useRef<HTMLHeadingElement>(null);
  const globalErrorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;

    const token = takeConfirmationToken();
    if (!token) {
      setStatus('invalid');
      return;
    }

    void authApi.confirmEmailChange(token).then(() => {
      clearSession();
      setStatus('changed');
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
        setGlobalError('Não foi possível confirmar a troca de e-mail agora. Abra novamente o link recebido para tentar mais tarde.');
        setStatus('unavailable');
      }
    });
  }, [clearSession, startCountdown]);

  useEffect(() => {
    if (status !== 'checking') titleRef.current?.focus();
  }, [status]);

  useEffect(() => {
    if (globalError) globalErrorRef.current?.focus();
  }, [globalError]);

  const title = status === 'checking'
    ? 'Confirmando a troca de e-mail…'
    : status === 'changed'
      ? 'E-mail alterado'
      : status === 'invalid'
        ? 'Link inválido ou expirado'
        : 'Não foi possível confirmar agora';
  const description = status === 'checking'
    ? 'Estamos verificando o link de troca.'
    : status === 'changed'
      ? 'E-mail alterado. Entre novamente com o novo endereço.'
      : status === 'invalid'
        ? 'O link de troca é inválido ou expirou. Entre na sua conta e solicite a troca novamente em Minha conta.'
        : 'Abra novamente o link recebido para tentar mais tarde.';
  const retryMessage = countdown.isWaiting
    ? 'Muitas solicitações. ' + retryCountdownMessage(countdown.remainingSeconds ?? 0) + ' Abra novamente o link depois desse prazo.'
    : countdown.remainingSeconds === 0
      ? 'O prazo de espera terminou. Abra novamente o link recebido para tentar mais uma vez.'
      : 'Muitas solicitações. Tente novamente mais tarde.';

  return <AuthLayout
    title={title}
    titleId="confirm-email-change-title"
    description={description}
    titleRef={titleRef}
    titleFocusable={status !== 'checking'}
  >
    {status === 'checking' && <p className="login-notice" role="status">Verificando o link…</p>}
    {status === 'changed' && <Link className="access-signin" to="/entrar">Entrar <ArrowRight size={17} aria-hidden="true"/></Link>}
    {status === 'invalid' && <>
      <p className="auth-secondary-link">Você poderá solicitar um novo link em Minha conta.</p>
      <Link className="access-signin" to="/perfil">Minha conta <ArrowRight size={17} aria-hidden="true"/></Link>
    </>}
    {status === 'unavailable' && <>
      {globalError && <div ref={globalErrorRef} className="login-error" role="alert" tabIndex={-1}>{globalError}</div>}
      {rateLimited && <p className={countdown.isWaiting ? 'login-error' : 'login-notice'} role={countdown.isWaiting ? 'alert' : 'status'}>
        {rateLimitWithoutRetryAfter ? 'Muitas solicitações. Tente novamente mais tarde.' : retryMessage}
      </p>}
      <Link className="auth-secondary-link" to="/entrar">Ir para entrar</Link>
    </>}
  </AuthLayout>;
}
