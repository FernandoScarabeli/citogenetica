import { FloatingNotice } from './components/ui/floating-notice';
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { RefreshCw, X } from 'lucide-react';
import { conviteApi } from './api/convite-api';
import type { ConviteLaboratorioResponseDTO } from './api/contracts/convite';
import type { PapelLaboratorio } from './api/contracts/laboratorio';
import { PAPEL_LABEL } from './api/contracts/laboratorio';
import { isConflict, isForbidden, isNotFound, isRateLimited, isUnauthorized } from './api/errors';
import { RoleFields } from './RoleFields';
import { retryCountdownMessage, useRetryCountdown } from './app/useRetryCountdown';

type InvitationLoadStatus = 'loading' | 'ready' | 'error';

function formatExpiration(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

function orderedRoles(roles: PapelLaboratorio[]): PapelLaboratorio[] {
  return (['MEMBRO', 'ADMIN', 'ORIENTADOR'] as PapelLaboratorio[]).filter(role => roles.includes(role));
}

function CancelInvitationDialog({ invitation, busy, onClose, onConfirm }: {
  invitation: ConviteLaboratorioResponseDTO;
  busy: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const element = dialog.current;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    if (element && !element.open) element.showModal();
    element?.querySelector<HTMLButtonElement>('[data-cancel]')?.focus();
    return () => {
      if (element?.open) element.close();
      previousFocus?.focus();
    };
  }, []);

  return <dialog className="laboratory-dialog" ref={dialog} aria-labelledby="cancel-invitation-title" aria-describedby="cancel-invitation-description" onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    <div className="dialog-heading"><h2 id="cancel-invitation-title">Cancelar convite?</h2><button className="icon-button" type="button" aria-label="Fechar" disabled={busy} onClick={onClose}><X size={19} aria-hidden="true" /></button></div>
    <p id="cancel-invitation-description">O convite enviado para <strong>{invitation.emailDestino}</strong> deixará de ser válido.</p>
    <div className="dialog-actions"><button data-cancel className="button secondary" type="button" disabled={busy} onClick={onClose}>Manter convite</button><button className="button danger" type="button" disabled={busy} onClick={onConfirm}>{busy ? 'Cancelando…' : 'Cancelar convite'}</button></div>
  </dialog>;
}

export function LaboratoryInvitations({ laboratorioId, onNotice }: { laboratorioId: string; onNotice?: (message: string) => void }) {
  const [invitations, setInvitations] = useState<ConviteLaboratorioResponseDTO[]>([]);
  const [loadStatus, setLoadStatus] = useState<InvitationLoadStatus>('loading');
  const [email, setEmail] = useState('');
  const [roles, setRoles] = useState<PapelLaboratorio[]>(['MEMBRO']);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [cancelCandidate, setCancelCandidate] = useState<ConviteLaboratorioResponseDTO | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [notice, setNotice] = useState('');
  useEffect(() => {
    if (notice && onNotice) {
      onNotice(notice);
      setNotice('');
    }
  }, [notice, onNotice]);
  const [rateLimitedWithoutRetryAfter, setRateLimitedWithoutRetryAfter] = useState(false);
  const countdown = useRetryCountdown();

  const refresh = useCallback(async (preserveMessages = false, signal?: AbortSignal) => {
    setLoadStatus('loading');
    if (!preserveMessages) {
      setErrorMessage('');
      setNotice('');
    }
    try {
      const result = await conviteApi.listar(laboratorioId, signal);
      if (signal?.aborted) return;
      setInvitations(result);
      setLoadStatus('ready');
    } catch (error) {
      if (signal?.aborted || isUnauthorized(error)) return;
      setLoadStatus('error');
      if (isForbidden(error)) setErrorMessage('Você não tem permissão para consultar os convites deste laboratório.');
      else if (isNotFound(error)) setErrorMessage('Este laboratório não foi encontrado. Atualize a página e tente novamente.');
      else setErrorMessage(error instanceof Error && error.message ? error.message : 'Não foi possível carregar os convites pendentes.');
    }
  }, [laboratorioId]);

  useEffect(() => {
    const controller = new AbortController();
    void refresh(false, controller.signal);
    return () => controller.abort();
  }, [refresh]);

  function showRateLimit(error: unknown) {
    if (!isRateLimited(error)) return false;
    const seconds = error.retryAfterSeconds;
    setRateLimitedWithoutRetryAfter(!(seconds && seconds > 0));
    countdown.start(seconds);
    setErrorMessage('rate-limited');
    return true;
  }

  function describeError(error: unknown, fallback: string): string {
    if (isForbidden(error)) return 'Você não tem permissão para administrar os convites deste laboratório.';
    if (isNotFound(error)) return 'Este laboratório ou convite não está mais disponível. Atualize a lista antes de continuar.';
    if (isConflict(error)) return 'Este endereço já participa do laboratório ou tem um convite pendente. Confira a lista e use Reenviar se houver um convite pendente.';
    return error instanceof Error && error.message ? error.message : fallback;
  }

  async function createInvitation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedEmail = email.trim();
    if (!normalizedEmail || busyAction || countdown.isWaiting) return;

    setBusyAction('create');
    setErrorMessage('');
    setNotice('');
    countdown.clear();
    try {
      const created = await conviteApi.criar(laboratorioId, normalizedEmail, orderedRoles(roles));
      setInvitations(current => [created, ...current.filter(invitation => invitation.id !== created.id)]);
      setEmail('');
      setRoles(['MEMBRO']);
      setNotice(`Convite enviado para ${created.emailDestino}.`);
    } catch (error) {
      if (isUnauthorized(error)) return;
      if (!showRateLimit(error)) {
        setErrorMessage(describeError(error, 'Não foi possível enviar o convite.'));
        if (isConflict(error)) void refresh(true);
      }
    } finally {
      setBusyAction(null);
    }
  }

  async function resendInvitation(invitation: ConviteLaboratorioResponseDTO) {
    if (busyAction || countdown.isWaiting) return;
    setBusyAction(`resend:${invitation.id}`);
    setErrorMessage('');
    setNotice('');
    countdown.clear();
    try {
      const resent = await conviteApi.reenviar(laboratorioId, invitation.id);
      setInvitations(current => current.map(item => item.id === resent.id ? resent : item));
      setNotice(`Convite reenviado para ${resent.emailDestino}.`);
    } catch (error) {
      if (isUnauthorized(error)) return;
      if (!showRateLimit(error)) {
        setErrorMessage(describeError(error, 'Não foi possível reenviar o convite.'));
        if (isConflict(error) || isNotFound(error)) void refresh(true);
      }
    } finally {
      setBusyAction(null);
    }
  }

  async function cancelInvitation() {
    if (!cancelCandidate || busyAction) return;
    const target = cancelCandidate;
    setBusyAction(`cancel:${target.id}`);
    setErrorMessage('');
    setNotice('');
    try {
      await conviteApi.revogar(laboratorioId, target.id);
      setInvitations(current => current.filter(item => item.id !== target.id));
      setCancelCandidate(null);
      setNotice(`Convite para ${target.emailDestino} cancelado.`);
    } catch (error) {
      if (isUnauthorized(error)) return;
      setErrorMessage(describeError(error, 'Não foi possível cancelar o convite.'));
      setCancelCandidate(null);
      if (isConflict(error) || isNotFound(error)) void refresh(true);
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

  return <>
    <section className="invitation-management" aria-labelledby="invitations-title">
      <div className="invitation-management-heading">
        <div><h2 id="invitations-title">Convidar participantes</h2><p>O papel Membro será incluído em toda participação.</p></div>
        <button className="text-button" type="button" aria-label="Atualizar convites" onClick={() => void refresh()} disabled={loadStatus === 'loading'}><RefreshCw size={15} aria-hidden="true" /><span>Atualizar convites</span></button>
      </div>
      <form className="invitation-create-form" onSubmit={event => void createInvitation(event)}>
        <div className="invitation-email-field"><label htmlFor="invitation-email">E-mail</label><input id="invitation-email" name="email" type="email" autoComplete="email" required maxLength={254} value={email} onChange={event => setEmail(event.target.value)} placeholder="nome@instituicao.br" /></div>
        <RoleFields value={roles} onChange={setRoles} />
        <button className="button primary" type="submit" disabled={!email.trim() || actionDisabled}>{busyAction === 'create' ? 'Enviando…' : countdown.isWaiting ? `Aguarde ${countdown.remainingSeconds} s` : 'Convidar'}</button>
      </form>
      {errorMessage && <p className={errorMessage === 'rate-limited' && !countdown.isWaiting ? 'invitation-notice' : 'invitation-error'} role={errorMessage === 'rate-limited' && !countdown.isWaiting ? 'status' : 'alert'}>
        {errorMessage === 'rate-limited' ? (rateLimitedWithoutRetryAfter ? 'Muitas solicitações. Tente novamente mais tarde.' : rateLimitMessage) : errorMessage}
      </p>}
      {!onNotice && <FloatingNotice message={notice} onClose={() => setNotice('')} />}

      <section className="pending-invitations" aria-labelledby="pending-invitations-title" aria-busy={loadStatus === 'loading'}>
        <div className="section-heading"><h2 id="pending-invitations-title">Convites pendentes</h2></div>
        {loadStatus === 'loading' ? <p className="invitation-list-state" role="status">Carregando convites…</p>
          : loadStatus === 'error' ? <div className="invitation-list-state"><p>{errorMessage || 'Não foi possível carregar os convites pendentes.'}</p><button className="text-button" type="button" onClick={() => void refresh()}>Tentar novamente</button></div>
            : invitations.length === 0 ? <p className="invitation-list-state">Não há convites pendentes.</p>
              : <ul className="invitation-list">{invitations.map(invitation => {
                const rowBusy = busyAction === `resend:${invitation.id}` || busyAction === `cancel:${invitation.id}`;
                return <li className="invitation-row" key={invitation.id}>
                  <div className="invitation-row-details">
                    <strong className="invitation-email">{invitation.emailDestino}</strong>
                    <div className="role-chips" aria-label={`Papéis: ${orderedRoles(invitation.papeis).map(role => PAPEL_LABEL[role]).join(', ')}`}>
                      {orderedRoles(invitation.papeis).map(role => <span className="role-chip" key={role}>{PAPEL_LABEL[role]}</span>)}
                    </div>
                    <time dateTime={invitation.expiraEm}>Válido até {formatExpiration(invitation.expiraEm)}</time>
                  </div>
                  <div className="invitation-row-actions">
                    <button className="text-button" type="button" disabled={actionDisabled} onClick={() => void resendInvitation(invitation)}>{busyAction === `resend:${invitation.id}` ? 'Reenviando…' : 'Reenviar'}</button>
                    <button className="text-button danger-text" type="button" disabled={actionDisabled} aria-label={`Cancelar convite para ${invitation.emailDestino}`} onClick={() => setCancelCandidate(invitation)}>{rowBusy && busyAction === `cancel:${invitation.id}` ? 'Cancelando…' : 'Cancelar'}</button>
                  </div>
                </li>;
              })}</ul>}
      </section>
    </section>
    {cancelCandidate && <CancelInvitationDialog invitation={cancelCandidate} busy={busyAction === `cancel:${cancelCandidate.id}`} onClose={() => setCancelCandidate(null)} onConfirm={() => void cancelInvitation()} />}
  </>;
}
