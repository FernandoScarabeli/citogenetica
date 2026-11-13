import { useEffect, useRef, useState } from 'react';
import { ArrowRight } from 'lucide-react';
import { Link, useNavigate } from 'react-router';
import { ApiError, NetworkError, isRateLimited } from '../api/errors';
import { conviteApi } from '../api/convite-api';
import { orientacaoApi } from '../api/orientacao-api';
import type { PreviaConviteResponseDTO } from '../api/contracts/convite';
import type { PapelLaboratorio } from '../api/contracts/laboratorio';
import { AuthLayout } from './AuthLayout';
import { useInvitationToken } from './invitation-token-context';
import { clearPendingInvitation, invitationPath, readPendingInvitation, savePendingInvitation } from './pending-invitation';
import { useSession } from './SessionProvider';
import { retryCountdownMessage, useRetryCountdown } from './useRetryCountdown';

type ResultState = 'ready' | 'refused' | 'invalid' | 'expired';
type InvitationAction = 'accept' | 'refuse';
// 'unavailable': a prévia não carregou, mas ainda dá para responder ao convite sem os nomes.
type PreviewState = { status: 'loading' } | { status: 'ready'; data: PreviaConviteResponseDTO } | { status: 'unavailable' };

const INVALID_INVITATION = 'Convite inválido ou indisponível. Ele pode ter expirado, sido cancelado ou ser para outro e-mail.';
const EXPIRED_INVITATION = 'Este convite expirou. Peça um novo convite a quem convidou você.';
const ROLE_NAMES: Record<PapelLaboratorio, string> = {
  MEMBRO: 'Participante',
  ADMIN: 'Administrador do laboratório',
  ORIENTADOR: 'Orientador',
};

export function InvitationAcceptancePage({ kind = 'laboratory' }: { kind?: 'laboratory' | 'orientation' }) {
  const { token: memoryToken, clearToken } = useInvitationToken();
  const { user, status: sessionStatus, loadError, retry, logout } = useSession();
  const navigate = useNavigate();
  // Sem token no link (ex.: voltou do cadastro em outra aba), usa o convite guardado.
  const [storedToken, setStoredToken] = useState<string | null>(() => {
    const pending = readPendingInvitation();
    return pending && pending.kind === kind ? pending.token : null;
  });
  const token = memoryToken ?? storedToken;
  const [result, setResult] = useState<ResultState>('ready');
  const [preview, setPreview] = useState<PreviewState>({ status: 'loading' });
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

  useEffect(() => {
    // Espera saber se há sessão: é a API que diz se o convite é para a conta aberta.
    if (!token || sessionStatus === 'loading') return;
    const controller = new AbortController();
    setPreview({ status: 'loading' });
    const request = kind === 'orientation'
      ? orientacaoApi.previaConvite(token, controller.signal)
      : conviteApi.previa(token, controller.signal);
    request.then((data) => {
      if (data.estado === 'EXPIRADO') finish('expired');
      else setPreview({ status: 'ready', data });
    }).catch((error: unknown) => {
      if (controller.signal.aborted) return;
      if (error instanceof ApiError && error.status === 400) finish('invalid');
      else setPreview({ status: 'unavailable' });
    });
    return () => controller.abort();
    // `finish` só mexe em estados estáveis; o convite é consultado de novo apenas quando o token muda.
  }, [token, kind, sessionStatus]);

  /** Convite encerrado (respondido, expirado ou inválido): esquece o token em todos os lugares. */
  function finish(next: ResultState) {
    clearToken();
    setStoredToken(null);
    clearPendingInvitation();
    setResult(next);
  }

  /** Guarda o convite antes de sair da tela para entrar ou criar conta. */
  function rememberInvitation() {
    if (token) savePendingInvitation({ token, kind });
  }

  async function switchAccount() {
    rememberInvitation();
    await logout(invitationPath(kind));
  }

  async function respond(action: InvitationAction) {
    if (!token || requestInFlight.current || busy || countdown.isWaiting || result !== 'ready') return;

    requestInFlight.current = true;
    setBusy(true);
    setMessage('');
    setRateLimitedWithoutRetryAfter(false);
    countdown.clear();

    try {
      if (action === 'accept') {
        if (kind === 'orientation') await orientacaoApi.aceitarConvite(token);
        else await conviteApi.aceitar(token);
        clearToken();
        setStoredToken(null);
        clearPendingInvitation();
        navigate('/laboratorios', { replace: true, state: { notice: kind === 'orientation'
          ? 'Convite de orientação aceito. Você agora é orientado e participa do laboratório.'
          : 'Convite aceito. O laboratório foi adicionado à sua lista.' } });
      } else {
        if (kind === 'orientation') await orientacaoApi.recusarConvite(token);
        else await conviteApi.recusar(token);
        finish('refused');
      }
    } catch (error) {
      if (isRateLimited(error)) {
        setRateLimitedWithoutRetryAfter(!(error.retryAfterSeconds && error.retryAfterSeconds > 0));
        startCountdown(error.retryAfterSeconds);
        setMessage('rate-limited');
      } else if (error instanceof ApiError && error.status === 400) {
        finish('invalid');
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

  const authenticated = sessionStatus === 'authenticated' && !!user;
  const data = preview.status === 'ready' ? preview.data : null;
  const wrongAccount = authenticated && data !== null && data.paraContaAtual === false;
  const exitLink = authenticated
    ? <Link className="access-signin" to="/laboratorios">Ir para meus laboratórios <ArrowRight size={17} aria-hidden="true" /></Link>
    : <Link className="access-signin" to="/">Ir para o início <ArrowRight size={17} aria-hidden="true" /></Link>;

  const title = result === 'refused'
    ? 'Convite recusado'
    : result === 'invalid' || result === 'expired'
      ? 'Convite indisponível'
      : !token
        ? 'Link de convite indisponível'
        : 'Responder ao convite';

  const description = result === 'refused'
    ? 'Sua resposta foi registrada.'
    : result === 'invalid' || result === 'expired'
      ? 'Não foi possível usar este convite.'
      : !token
        ? 'Abra novamente o link recebido por e-mail para continuar.'
        : data
          ? kind === 'orientation'
            ? <><strong>{data.convidadoPorNome}</strong> quer orientar você no laboratório <strong>{data.laboratorioNome}</strong>.</>
            : <><strong>{data.convidadoPorNome}</strong> convidou você para o laboratório <strong>{data.laboratorioNome}</strong>.</>
          : preview.status === 'loading'
            ? 'Carregando o convite…'
            : 'Aceite ou recuse o convite recebido por e-mail.';

  const retryMessage = countdown.isWaiting
    ? `Muitas tentativas. ${retryCountdownMessage(countdown.remainingSeconds ?? 0)}`
    : countdown.remainingSeconds === 0
      ? 'O prazo de espera terminou. Você já pode tentar novamente.'
      : 'Muitas tentativas. Tente novamente mais tarde.';

  const showInvitationDetails = result === 'ready' && token && data;

  return <AuthLayout
    title={title}
    titleId="invitation-title"
    description={description}
    titleRef={titleRef}
    titleFocusable={result !== 'ready'}
  >
    {showInvitationDetails && <div className="invitation-details">
      <p>Convite enviado para <strong>{data.emailMascarado}</strong>.</p>
      {kind === 'laboratory' && data.papeis.length > 0 && <p>Você entraria como: {data.papeis.map((papel) => ROLE_NAMES[papel]).join(', ')}.</p>}
      {kind === 'orientation' && <p>Ao aceitar, você passará a ser orientado por esta pessoa e entrará no laboratório como Membro caso ainda não participe. Você também pode recusar o convite.</p>}
    </div>}
    {!token && result === 'ready' && <>
      <p className="login-notice" role="status">Por segurança, o convite não fica guardado. Abra novamente o link recebido por e-mail.</p>
      <div className="invitation-actions">{exitLink}</div>
    </>}
    {sessionStatus === 'loading' && token && result === 'ready' && <p className="login-notice" role="status">Verificando sua sessão…</p>}
    {sessionStatus === 'error' && token && result === 'ready' && <div className="invitation-session-error" role="alert">
      <p>{loadError instanceof NetworkError ? 'Não foi possível conectar à API. Verifique sua conexão.' : 'Não foi possível verificar sua sessão agora.'}</p>
      <button className="access-signin" type="button" onClick={() => void retry()}>Tentar novamente</button>
    </div>}
    {token && result === 'ready' && sessionStatus === 'unauthenticated' && <div className="invitation-sign-in">
      <p>Entre ou crie sua conta com o e-mail que recebeu o convite{data ? ` (${data.emailMascarado})` : ''}.</p>
      <div className="invitation-actions">
        <Link className="access-signin" to="/entrar" onClick={rememberInvitation} state={{ returnTo: invitationPath(kind) }}>Entrar <ArrowRight size={17} aria-hidden="true" /></Link>
        <Link className="invitation-secondary-action" to="/criar-conta" onClick={rememberInvitation}>Criar conta</Link>
      </div>
    </div>}
    {token && result === 'ready' && authenticated && wrongAccount && data && <div className="invitation-sign-in">
      <div className="login-notice" role="alert">Este convite é para {data.emailMascarado}. Você entrou como {user.email}.</div>
      <div className="invitation-actions">
        <button className="access-signin" type="button" onClick={() => void switchAccount()}>Entrar com outra conta</button>
        <Link className="invitation-secondary-action" to="/laboratorios">Ir para meus laboratórios</Link>
      </div>
    </div>}
    {token && result === 'ready' && authenticated && !wrongAccount && <>
      <p className="invitation-account">Você entrou como <strong>{user.email}</strong>.</p>
      {message && <div ref={messageRef} className={message === 'rate-limited' && countdown.isWaiting ? 'login-error' : 'login-notice'} role={message === 'rate-limited' && !countdown.isWaiting ? 'status' : 'alert'} tabIndex={-1}>
        {message === 'rate-limited' ? (rateLimitedWithoutRetryAfter ? 'Muitas tentativas. Tente novamente mais tarde.' : retryMessage) : message}
      </div>}
      <div className="invitation-actions">
        <button className="access-signin" type="button" disabled={busy || countdown.isWaiting || preview.status === 'loading'} onClick={() => void respond('accept')}>
          {busy ? 'Enviando…' : countdown.isWaiting ? `Aguarde ${countdown.remainingSeconds} s` : 'Aceitar convite'}
        </button>
        <button className="invitation-secondary-action" type="button" disabled={busy || countdown.isWaiting || preview.status === 'loading'} onClick={() => void respond('refuse')}>
          Recusar
        </button>
      </div>
    </>}
    {result === 'refused' && <>
      <p className="login-notice" role="status">Você recusou o convite.</p>
      <div className="invitation-actions">{exitLink}</div>
    </>}
    {result === 'invalid' && <>
      <div className="login-error" role="alert">{INVALID_INVITATION}</div>
      <div className="invitation-actions">{exitLink}</div>
    </>}
    {result === 'expired' && <>
      <div className="login-error" role="alert">{EXPIRED_INVITATION}</div>
      <div className="invitation-actions">{exitLink}</div>
    </>}
  </AuthLayout>;
}
