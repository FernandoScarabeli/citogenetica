import { FloatingNotice } from './components/ui/floating-notice';
import { useCallback, useEffect, useRef, useState, type FormEvent, type RefObject } from 'react';
import { RefreshCw } from 'lucide-react';
import { NetworkError, isConflict, isForbidden, isNotFound, isRateLimited, isUnauthorized } from './api/errors';
import type { ConviteOrientacaoResponseDTO, VinculoOrientacaoResponseDTO } from './api/contracts/orientacao';
import { orientacaoApi } from './api/orientacao-api';
import { retryCountdownMessage, useRetryCountdown } from './app/useRetryCountdown';

type Confirmation =
  | { kind: 'end'; link: VinculoOrientacaoResponseDTO }
  | { kind: 'cancel'; invitation: ConviteOrientacaoResponseDTO };

type LoadStatus = 'loading' | 'ready' | 'error';

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium' }).format(new Date(value));
}

function toInvitationError(error: unknown, fallback: string): string {
  if (isForbidden(error)) return 'Você precisa ter o papel Orientador neste laboratório para gerenciar convites de orientação.';
  if (isNotFound(error)) return 'O laboratório, sua participação ou o convite não está mais disponível. Atualize a lista antes de continuar.';
  if (isConflict(error)) {
    const message = error.message.toLocaleLowerCase('pt-BR');
    if (message.includes('si mesmo')) return 'Você não pode convidar a si mesmo para orientação.';
    if (message.includes('vínculo ativo')) return 'Este pesquisador já possui uma orientação ativa com você neste laboratório.';
    if (message.includes('convite') && message.includes('pendente')) return 'Já existe um convite de orientação pendente para este e-mail. Use Reenviar na lista.';
    return 'O convite não pode ser alterado porque o laboratório ou o convite mudou. Atualize a lista e tente novamente.';
  }
  if (error instanceof NetworkError) return error.message;
  return error instanceof Error && error.message ? error.message : fallback;
}

function toLinkError(error: unknown, fallback: string): string {
  if (isForbidden(error)) return 'Somente o orientador deste vínculo ou um administrador pode encerrá-lo.';
  if (isNotFound(error)) return 'O laboratório, sua participação ou o vínculo não está mais disponível. Atualize a lista antes de continuar.';
  if (isConflict(error)) return 'Este vínculo já foi encerrado. A lista foi atualizada.';
  if (error instanceof NetworkError) return error.message;
  return error instanceof Error && error.message ? error.message : fallback;
}

function ConfirmationDialog({ confirmation, busy, fallbackFocus, onClose, onConfirm }: {
  confirmation: Confirmation;
  busy: boolean;
  fallbackFocus: RefObject<HTMLHeadingElement | null>;
  onClose: () => void;
  onConfirm: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const cancelButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const element = dialog.current;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    if (element && !element.open) element.showModal();
    cancelButton.current?.focus();
    return () => {
      if (element?.open) element.close();
      if (previousFocus?.isConnected) previousFocus.focus();
      else fallbackFocus.current?.focus();
    };
  }, [fallbackFocus]);

  const ending = confirmation.kind === 'end';
  const label = ending ? 'Encerrar orientação' : 'Cancelar convite';
  const person = ending
    ? `${confirmation.link.nomeOrientador} e ${confirmation.link.nomePesquisador}`
    : confirmation.invitation.emailDestino;

  return <dialog ref={dialog} className="laboratory-dialog orientation-confirmation" aria-labelledby="orientation-confirm-title" aria-describedby="orientation-confirm-description" onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    <div className="dialog-heading"><div>
      <h2 id="orientation-confirm-title">{ending ? 'Encerrar orientação?' : 'Cancelar convite de orientação?'}</h2>
      <p id="orientation-confirm-description">{ending
        ? `O vínculo entre ${person} será encerrado.`
        : `O convite enviado para ${person} deixará de ser válido.`}</p>
    </div></div>
    <div className="dialog-actions">
      <button ref={cancelButton} className="button secondary" type="button" disabled={busy} onClick={onClose}>{ending ? 'Manter orientação' : 'Manter convite'}</button>
      <button className="button danger" type="button" disabled={busy} onClick={onConfirm}>{busy ? 'Aguarde…' : label}</button>
    </div>
  </dialog>;
}

export function LaboratoryOrientations({ laboratorioId, userId, isOrientador, isAdmin, laboratorioAtivo, onNotice }: {
  onNotice?: (message: string) => void;
  laboratorioId: string;
  userId: number;
  isOrientador: boolean;
  isAdmin: boolean;
  laboratorioAtivo: boolean;
}) {
  const [links, setLinks] = useState<VinculoOrientacaoResponseDTO[]>([]);
  const [linkStatus, setLinkStatus] = useState<LoadStatus>('loading');
  const [invitations, setInvitations] = useState<ConviteOrientacaoResponseDTO[]>([]);
  const [invitationStatus, setInvitationStatus] = useState<LoadStatus>('loading');
  const [email, setEmail] = useState('');
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [notice, setNotice] = useState('');
  useEffect(() => {
    if (notice && onNotice) {
      onNotice(notice);
      setNotice('');
    }
  }, [notice, onNotice]);
  const [rateLimitedWithoutRetryAfter, setRateLimitedWithoutRetryAfter] = useState(false);
  const activeLinksHeading = useRef<HTMLHeadingElement>(null);
  const pendingInvitationsHeading = useRef<HTMLHeadingElement>(null);
  const countdown = useRetryCountdown();
  const canManageInvitations = laboratorioAtivo && isOrientador;

  const loadLinks = useCallback(async (signal?: AbortSignal) => {
    setLinkStatus('loading');
    try {
      const result = await orientacaoApi.listarVinculos(laboratorioId, signal);
      if (signal?.aborted) return;
      setLinks(result);
      setLinkStatus('ready');
    } catch (error) {
      if (signal?.aborted || isUnauthorized(error)) return;
      setErrorMessage(toLinkError(error, 'Não foi possível carregar os vínculos de orientação.'));
      setLinkStatus('error');
    }
  }, [laboratorioId]);

  const loadInvitations = useCallback(async (signal?: AbortSignal) => {
    if (!canManageInvitations) {
      setInvitations([]);
      setInvitationStatus('ready');
      return;
    }
    setInvitationStatus('loading');
    try {
      const result = await orientacaoApi.listarConvites(laboratorioId, signal);
      if (signal?.aborted) return;
      setInvitations(result);
      setInvitationStatus('ready');
    } catch (error) {
      if (signal?.aborted || isUnauthorized(error)) return;
      setErrorMessage(toInvitationError(error, 'Não foi possível carregar os convites de orientação.'));
      setInvitationStatus('error');
    }
  }, [canManageInvitations, laboratorioId]);

  const refresh = useCallback((clearError = true) => {
    if (clearError) setErrorMessage('');
    void loadLinks();
    void loadInvitations();
  }, [loadInvitations, loadLinks]);

  useEffect(() => {
    const linksController = new AbortController();
    const invitationsController = new AbortController();
    void loadLinks(linksController.signal);
    void loadInvitations(invitationsController.signal);
    return () => {
      linksController.abort();
      invitationsController.abort();
    };
  }, [loadInvitations, loadLinks]);

  function showRateLimit(error: unknown): boolean {
    if (!isRateLimited(error)) return false;
    setRateLimitedWithoutRetryAfter(!(error.retryAfterSeconds && error.retryAfterSeconds > 0));
    countdown.start(error.retryAfterSeconds);
    setErrorMessage('rate-limited');
    return true;
  }

  async function createInvitation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const emailDestino = email.trim();
    if (!canManageInvitations || !emailDestino || busyAction || countdown.isWaiting) return;
    setBusyAction('create');
    setErrorMessage('');
    setNotice('');
    countdown.clear();
    try {
      const invitation = await orientacaoApi.criarConvite(laboratorioId, emailDestino);
      setInvitations(current => [invitation, ...current.filter(item => item.id !== invitation.id)]);
      setEmail('');
      setNotice(`Convite de orientação enviado para ${invitation.emailDestino}.`);
    } catch (error) {
      if (isUnauthorized(error)) return;
      if (!showRateLimit(error)) {
        setErrorMessage(toInvitationError(error, 'Não foi possível enviar o convite de orientação.'));
        if (isConflict(error)) refresh(false);
      }
    } finally {
      setBusyAction(null);
    }
  }

  async function resendInvitation(invitation: ConviteOrientacaoResponseDTO) {
    if (!canManageInvitations || busyAction || countdown.isWaiting) return;
    setBusyAction(`resend:${invitation.id}`);
    setErrorMessage('');
    setNotice('');
    countdown.clear();
    try {
      const resent = await orientacaoApi.reenviarConvite(laboratorioId, invitation.id);
      setInvitations(current => current.map(item => item.id === resent.id ? resent : item));
      setNotice(`Convite de orientação reenviado para ${resent.emailDestino}.`);
    } catch (error) {
      if (isUnauthorized(error)) return;
      if (!showRateLimit(error)) {
        setErrorMessage(toInvitationError(error, 'Não foi possível reenviar o convite de orientação.'));
        if (isConflict(error) || isNotFound(error)) void loadInvitations();
      }
    } finally {
      setBusyAction(null);
    }
  }

  async function cancelInvitation() {
    if (confirmation?.kind !== 'cancel' || busyAction) return;
    const target = confirmation.invitation;
    setBusyAction(`cancel:${target.id}`);
    setErrorMessage('');
    setNotice('');
    try {
      await orientacaoApi.cancelarConvite(laboratorioId, target.id);
      setInvitations(current => current.filter(item => item.id !== target.id));
      setConfirmation(null);
      setNotice(`Convite de orientação para ${target.emailDestino} cancelado.`);
    } catch (error) {
      if (isUnauthorized(error)) return;
      setErrorMessage(toInvitationError(error, 'Não foi possível cancelar o convite de orientação.'));
      setConfirmation(null);
      if (isConflict(error) || isNotFound(error)) void loadInvitations();
    } finally {
      setBusyAction(null);
    }
  }

  async function endLink() {
    if (confirmation?.kind !== 'end' || busyAction || !laboratorioAtivo) return;
    const target = confirmation.link;
    setBusyAction(`end:${target.id}`);
    setErrorMessage('');
    setNotice('');
    try {
      await orientacaoApi.encerrarVinculo(laboratorioId, target.id);
      setLinks(current => current.filter(item => item.id !== target.id));
      setConfirmation(null);
      setNotice(`Orientação entre ${target.nomeOrientador} e ${target.nomePesquisador} encerrada.`);
    } catch (error) {
      if (isUnauthorized(error)) return;
      setErrorMessage(toLinkError(error, 'Não foi possível encerrar a orientação.'));
      setConfirmation(null);
      if (isConflict(error) || isNotFound(error)) void loadLinks();
    } finally {
      setBusyAction(null);
    }
  }

  const actionDisabled = busyAction !== null || countdown.isWaiting;
  const rateLimitMessage = countdown.isWaiting
    ? `Muitas solicitações. ${retryCountdownMessage(countdown.remainingSeconds ?? 0)}`
    : countdown.remainingSeconds === 0
      ? 'O prazo de espera terminou. Você já pode tentar novamente.'
      : 'Muitas solicitações. Tente novamente mais tarde.';
  const canClose = (link: VinculoOrientacaoResponseDTO) => laboratorioAtivo
    && (isAdmin || (isOrientador && link.orientadorUsuarioId === userId));

  return <section className="orientation-management" aria-labelledby="orientation-title">
    <div className="orientation-heading">
      <div>
        <h2 id="orientation-title">Orientação</h2>
        <p>{laboratorioAtivo
          ? 'Vínculos ativos de orientação visíveis para você neste laboratório.'
          : 'Este laboratório está desativado. As orientações estão disponíveis somente para consulta.'}</p>
      </div>
      <button className="text-button" type="button" onClick={() => refresh()} disabled={linkStatus === 'loading' || invitationStatus === 'loading'}>
        <RefreshCw size={15} aria-hidden="true" />Atualizar
      </button>
    </div>

    {errorMessage && <p className={errorMessage === 'rate-limited' && !countdown.isWaiting ? 'orientation-notice' : 'orientation-error'} role={errorMessage === 'rate-limited' && !countdown.isWaiting ? 'status' : 'alert'}>
      {errorMessage === 'rate-limited' ? (rateLimitedWithoutRetryAfter ? 'Muitas solicitações. Tente novamente mais tarde.' : rateLimitMessage) : errorMessage}
    </p>}
    {!onNotice && <FloatingNotice message={notice} onClose={() => setNotice('')} />}

    <section className="active-orientations" aria-labelledby="active-orientations-title" aria-busy={linkStatus === 'loading'}>
      <div className="section-heading"><h3 ref={activeLinksHeading} id="active-orientations-title" tabIndex={-1}>Vínculos ativos</h3></div>
      {linkStatus === 'loading' ? <p className="orientation-list-state" role="status">Carregando vínculos…</p>
        : linkStatus === 'error' ? <div className="orientation-list-state"><p>Não foi possível carregar os vínculos de orientação.</p><button className="text-button" type="button" onClick={() => void loadLinks()}>Tentar novamente</button></div>
          : links.length === 0 ? <p className="orientation-list-state">Não há vínculos ativos visíveis para você.</p>
            : <ul className="orientation-list">{links.map(link => <li className="orientation-row" key={link.id}>
              <div className="orientation-people">
                <strong>{link.nomeOrientador}</strong><span>orienta</span><strong>{link.nomePesquisador}</strong>
                <time dateTime={link.criadoEm}>Desde {formatDate(link.criadoEm)}</time>
              </div>
              {canClose(link) && <button className="text-button danger-text" type="button" disabled={busyAction !== null} aria-label={`Encerrar orientação entre ${link.nomeOrientador} e ${link.nomePesquisador}`} onClick={() => setConfirmation({ kind: 'end', link })}>
                Encerrar orientação
              </button>}
            </li>)}</ul>}
    </section>

    {canManageInvitations && <section className="orientation-invitations" aria-labelledby="orientation-invitations-title" aria-busy={invitationStatus === 'loading'}>
      <div className="section-heading"><h3 id="orientation-invitations-title">Convidar para orientação</h3></div>
      <p className="orientation-invitation-explanation">A pessoa passará a ser orientada por você quando aceitar o convite. Se ainda não participar do laboratório, também entrará como Membro.</p>
      <form className="orientation-create-form" onSubmit={event => void createInvitation(event)}>
        <div className="orientation-email-field">
          <label htmlFor="orientation-invitation-email">E-mail da pessoa</label>
          <input id="orientation-invitation-email" name="email" type="email" autoComplete="email" required maxLength={254} value={email} onChange={event => setEmail(event.target.value)} placeholder="nome@instituicao.br" />
        </div>
        <button className="button primary" type="submit" disabled={!email.trim() || actionDisabled}>
          {busyAction === 'create' ? 'Enviando…' : countdown.isWaiting ? `Aguarde ${countdown.remainingSeconds} s` : 'Convidar para orientação'}
        </button>
      </form>
      <div className="pending-orientation-invitations">
        <div className="section-heading"><h3 ref={pendingInvitationsHeading} tabIndex={-1}>Convites pendentes</h3></div>
        {invitationStatus === 'loading' ? <p className="orientation-list-state" role="status">Carregando convites…</p>
          : invitationStatus === 'error' ? <div className="orientation-list-state"><p>Não foi possível carregar os convites de orientação.</p><button className="text-button" type="button" onClick={() => void loadInvitations()}>Tentar novamente</button></div>
            : invitations.length === 0 ? <p className="orientation-list-state">Não há convites de orientação pendentes.</p>
              : <ul className="orientation-list">{invitations.map(invitation => <li className="orientation-row" key={invitation.id}>
                <div className="orientation-people">
                  <strong>{invitation.emailDestino}</strong>
                  <span>Convite enviado em {formatDate(invitation.criadoEm)}</span>
                  <time dateTime={invitation.expiraEm}>Válido até {formatDate(invitation.expiraEm)}</time>
                </div>
                <div className="orientation-row-actions">
                  <button className="text-button" type="button" disabled={actionDisabled} onClick={() => void resendInvitation(invitation)}>{busyAction === `resend:${invitation.id}` ? 'Reenviando…' : 'Reenviar'}</button>
                  <button className="text-button danger-text" type="button" disabled={actionDisabled} aria-label={`Cancelar convite de orientação para ${invitation.emailDestino}`} onClick={() => setConfirmation({ kind: 'cancel', invitation })}>{busyAction === `cancel:${invitation.id}` ? 'Cancelando…' : 'Cancelar'}</button>
                </div>
              </li>)}</ul>}
      </div>
    </section>}

    {confirmation && <ConfirmationDialog
      confirmation={confirmation}
      busy={busyAction === `${confirmation.kind === 'end' ? 'end' : 'cancel'}:${confirmation.kind === 'end' ? confirmation.link.id : confirmation.invitation.id}`}
      fallbackFocus={confirmation.kind === 'end' ? activeLinksHeading : pendingInvitationsHeading}
      onClose={() => setConfirmation(null)}
      onConfirm={() => void (confirmation.kind === 'end' ? endLink() : cancelInvitation())}
    />}
  </section>;
}
