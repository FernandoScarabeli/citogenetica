import { useEffect, useRef, useState } from 'react';
import { ArrowRight } from 'lucide-react';
import { Link, useNavigate } from 'react-router';
import { ApiError, NetworkError, isRateLimited } from '../api/errors';
import { conviteApi } from '../api/convite-api';
import { AuthLayout } from './AuthLayout';
import { useInvitationToken } from './invitation-token-context';
import { useSession } from './SessionProvider';
import { retryCountdownMessage, useRetryCountdown } from './useRetryCountdown';

type ResultState = 'ready' | 'refused' | 'invalid';
type InvitationAction = 'accept' | 'refuse';

const INVALID_INVITATION = 'Convite inválido ou indisponível. Ele pode ter expirado, sido cancelado ou ser para outro e-mail.';

export function InvitationAcceptancePage() {
  const { token, clearToken } = useInvitationToken();
  const { user, status: sessionStatus, loadError, retry } = useSession();
  const navigate = useNavigate();
  const [result, setResult] = useState<ResultState>('ready');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [rateLimitedWithoutRetryAfter, setRateLimitedWithoutRetryAfter] = useState(false);
  const requestInFlight = useRef(false);
  const titleRef = useRef<HTMLHeadingElement>(null);
  const messageRef = useRef<HTMLDivElement>(null);
  const countdown = useRetryCountdown();
  const startCountdown = countdown.start;

  useEffect(() => {
    if (result !== 'ready') titleRef.current?.focus();
  }, [result]);

  useEffect(() => {
    if (message) messageRef.current?.focus();
  }, [message]);

  async function respond(action: InvitationAction) {
    if (!token || requestInFlight.current || busy || countdown.isWaiting || result !== 'ready') return;

    requestInFlight.current = true;
    setBusy(true);
    setMessage('');
    setRateLimitedWithoutRetryAfter(false);
    countdown.clear();

    try {
      if (action === 'accept') {
        await conviteApi.aceitar(token);
        clearToken();
        navigate('/laboratorios', { replace: true, state: { notice: 'Convite aceito. O laboratório foi adicionado à sua lista.' } });
      } else {
        await conviteApi.recusar(token);
        clearToken();
        setResult('refused');
      }
    } catch (error) {
      if (isRateLimited(error)) {
        setRateLimitedWithoutRetryAfter(!(error.retryAfterSeconds && error.retryAfterSeconds > 0));
        startCountdown(error.retryAfterSeconds);
        setMessage('rate-limited');
      } else if (error instanceof ApiError && error.status === 400) {
        clearToken();
        setResult('invalid');
      } else if (error instanceof ApiError && error.status === 403) {
        setMessage('Sua conta não tem permissão para responder a este convite.');
      } else if (error instanceof NetworkError) {
        setMessage('Não foi possível conectar à API. Verifique sua conexão e tente novamente.');
      } else {
        setMessage('Não foi possível responder ao convite agora. Tente novamente.');
      }
    } finally {
      requestInFlight.current = false;
      setBusy(false);
    }
  }

  const title = result === 'refused'
    ? 'Convite recusado'
    : result === 'invalid'
      ? 'Convite indisponível'
      : !token
        ? 'Link de convite indisponível'
        : 'Responder ao convite';

  const description = result === 'refused'
    ? 'Sua resposta foi registrada.'
    : result === 'invalid'
      ? 'Não foi possível usar este convite.'
      : !token
        ? 'O link precisa conter um convite ativo.'
        : 'Aceite ou recuse o convite recebido por e-mail.';

  const retryMessage = countdown.isWaiting
    ? `Muitas tentativas. ${retryCountdownMessage(countdown.remainingSeconds ?? 0)}`
    : countdown.remainingSeconds === 0
      ? 'O prazo de espera terminou. Você já pode tentar novamente.'
      : 'Muitas tentativas. Tente novamente mais tarde.';

  return <AuthLayout
    title={title}
    titleId="invitation-title"
    description={description}
    titleRef={titleRef}
    titleFocusable={result !== 'ready'}
  >
    {!token && result === 'ready' && <p className="login-notice" role="status">O token do convite fica somente nesta aba. Abra novamente o link recebido para continuar.</p>}
    {sessionStatus === 'loading' && token && <p className="login-notice" role="status">Verificando sua sessão…</p>}
    {sessionStatus === 'error' && token && <div className="invitation-session-error" role="alert">
      <p>{loadError instanceof NetworkError ? 'Não foi possível conectar à API. Verifique sua conexão.' : 'Não foi possível verificar sua sessão agora.'}</p>
      <button className="access-signin" type="button" onClick={() => void retry()}>Tentar novamente</button>
    </div>}
    {token && sessionStatus === 'unauthenticated' && <div className="invitation-sign-in">
      <p>Entre ou crie sua conta com o e-mail que recebeu o convite.</p>
      <p>Se precisar criar uma conta e confirmar o e-mail em outra aba ou pelo link recebido, abra novamente o link do convite depois da confirmação.</p>
      <div className="invitation-actions">
        <Link className="access-signin" to="/entrar" state={{ returnTo: '/convites/aceitar' }}>Entrar <ArrowRight size={17} aria-hidden="true" /></Link>
        <Link className="invitation-secondary-action" to="/criar-conta">Criar conta</Link>
      </div>
    </div>}
    {token && sessionStatus === 'authenticated' && user && result === 'ready' && <>
      <p className="invitation-account">Conta autenticada: <strong>{user.email}</strong></p>
      {message && <div ref={messageRef} className={message === 'rate-limited' && countdown.isWaiting ? 'login-error' : 'login-notice'} role={message === 'rate-limited' && !countdown.isWaiting ? 'status' : 'alert'} tabIndex={-1}>
        {message === 'rate-limited' ? (rateLimitedWithoutRetryAfter ? 'Muitas tentativas. Tente novamente mais tarde.' : retryMessage) : message}
      </div>}
      <div className="invitation-actions">
        <button className="access-signin" type="button" disabled={busy || countdown.isWaiting} onClick={() => void respond('accept')}>
          {busy ? 'Enviando…' : countdown.isWaiting ? `Aguarde ${countdown.remainingSeconds} s` : 'Aceitar convite'}
        </button>
        <button className="invitation-secondary-action" type="button" disabled={busy || countdown.isWaiting} onClick={() => void respond('refuse')}>
          Recusar
        </button>
      </div>
    </>}
    {result === 'refused' && <p className="login-notice" role="status">Você recusou o convite.</p>}
    {result === 'invalid' && <div className="login-error" role="alert">{INVALID_INVITATION}</div>}
  </AuthLayout>;
}
