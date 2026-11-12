import { Fragment, useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { ArrowRight, ChevronDown, ChevronRight, LogOut, Moon, Microscope, Plus, Search, ShieldCheck, UserRound, X } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router';
import { isConflict, isForbidden, isNotFound, isUnauthorized } from './api/errors';
import type { LaboratorioResponseDTO, PapelLaboratorio, ParticipacaoResponseDTO } from './api/contracts/laboratorio';
import { PAPEL_LABEL } from './api/contracts/laboratorio';
import { laboratorioApi } from './api/laboratorio-api';
import { contagemApi, type ContagemApi } from './api/contagem-api';
import { migrarLaminasLocais } from './migracao-automatica';
import { LaboratoryInvitations } from './LaboratoryInvitations';
import { LaboratoryOrientations } from './LaboratoryOrientations';
import { RoleFields } from './RoleFields';
import { typeClass } from './lib/type-scale';
import './laboratories.css';
import './laboratory-management.css';
import { FloatingNotice } from './components/ui/floating-notice';
import { TabTransition } from './components/ui/tab-transition';
import { loadPreferences, savePreferences } from './preferences';
import type { UsuarioResponseDTO } from './api/contracts/usuario';
import { ProfileSettings } from './app/ProfileSettings';
import { NotificationCenter } from './NotificationCenter';

const initialPreferences = loadPreferences();
type View = 'list' | 'manage' | 'slides' | 'profile';
type Confirmation = { kind: 'leave' } | { kind: 'remove'; participation: ParticipacaoResponseDTO };

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

function hasRole(papeis: PapelLaboratorio[], papel: PapelLaboratorio): boolean {
  return papeis.includes(papel);
}

function isCurrentUser(participation: ParticipacaoResponseDTO, email: string): boolean {
  return !!participation.email && participation.email.trim().toLocaleLowerCase('pt-BR') === email.trim().toLocaleLowerCase('pt-BR');
}

function AccountMenu({ name, email, photo, dark, platformAdmin, onProfile, onAppearance, onLogout }: {
  name: string;
  email: string;
  photo?: string;
  dark: boolean;
  platformAdmin: boolean;
  onProfile: () => void;
  onAppearance: () => void;
  onLogout: () => void;
}) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    function outside(event: PointerEvent) {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    }
    function key(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false);
        trigger.current?.focus();
      }
    }
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', key);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', key);
    };
  }, [open]);

  const initial = name.charAt(0).toLocaleUpperCase('pt-BR');
  return <div className="account-menu" ref={root}>
    <button ref={trigger} type="button" className="account-trigger" aria-label="Menu do perfil" aria-expanded={open} aria-controls={open ? 'account-panel' : undefined} onClick={() => setOpen(value => !value)}>
      <span className="account-initial" aria-hidden="true">{photo ? <img src={photo} alt="" /> : initial}</span>
      <span className="account-trigger-identity"><span className="account-trigger-name">{name}</span><span className="account-trigger-email">{email}</span></span>
      <ChevronDown size={16} aria-hidden="true" />
    </button>
    {open && <div className="account-panel" id="account-panel" role="group" aria-label="Menu do perfil">
      <div className="account-panel-identity"><span className="account-initial" aria-hidden="true">{photo ? <img src={photo} alt="" /> : initial}</span><div><strong>{name}</strong><small>{email}</small></div></div>
      <div className="account-panel-actions">
        <button type="button" onClick={() => { setOpen(false); onProfile(); }}><UserRound size={18} aria-hidden="true" />Configurações do perfil</button>
        {platformAdmin && <Link to="/admin/laboratorios" onClick={() => setOpen(false)}><ShieldCheck size={18} aria-hidden="true" />Administração da plataforma</Link>}
        <button type="button" aria-pressed={dark} onClick={onAppearance}><Moon size={18} aria-hidden="true" /><span>Modo escuro</span><span className="account-switch" aria-hidden="true"><span /></span></button>
      </div>
      <div className="account-panel-footer"><button type="button" onClick={() => { setOpen(false); onLogout(); }}><LogOut size={18} aria-hidden="true" />Sair</button></div>
    </div>}
  </div>;
}

function CreateLaboratoryDialog({ name, error, busy, onName, onClose, onSubmit }: {
  name: string;
  error: string;
  busy: boolean;
  onName: (value: string) => void;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const element = dialog.current;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    if (element && !element.open) element.showModal();
    element?.querySelector('input')?.focus();
    return () => {
      if (element?.open) element.close();
      previousFocus?.focus();
    };
  }, []);

  return <dialog ref={dialog} className="laboratory-dialog" aria-labelledby="create-lab-title" onCancel={event => { event.preventDefault(); onClose(); }}>
    <div className="dialog-heading"><div><h2 id="create-lab-title">Criar laboratório</h2><p>Quem cria o laboratório recebe o papel de administrador.</p></div><button className="icon-button" type="button" aria-label="Fechar" onClick={onClose}><X size={19} aria-hidden="true" /></button></div>
    <form onSubmit={onSubmit}>
      <label htmlFor="new-lab-name">Nome do laboratório</label>
      <input id="new-lab-name" required maxLength={120} value={name} onChange={event => onName(event.target.value)} placeholder="Ex.: Laboratório de Citogenética" />
      {error && <p className="dialog-error" role="alert">{error}</p>}
      <div className="dialog-actions"><button className="button secondary" type="button" onClick={onClose}>Cancelar</button><button className="button primary" type="submit" disabled={!name.trim() || busy}>{busy ? 'Criando…' : 'Criar laboratório'}</button></div>
    </form>
  </dialog>;
}

function ConfirmationDialog({ confirmation, busy, error, onClose, onConfirm }: {
  confirmation: Confirmation;
  busy: boolean;
  error: string;
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

  const removing = confirmation.kind === 'remove';
  const heading = removing ? `Remover ${confirmation.participation.nome}?` : 'Sair do laboratório?';
  const description = removing
    ? `A participação de ${confirmation.participation.nome} será encerrada.`
    : 'Sua participação será encerrada e este laboratório deixará de aparecer na sua lista.';

  return <dialog ref={dialog} className="laboratory-dialog" aria-labelledby="confirm-lab-title" aria-describedby="confirm-lab-description" onCancel={event => { event.preventDefault(); onClose(); }}>
    <div className="dialog-heading"><h2 id="confirm-lab-title">{heading}</h2><button className="icon-button" type="button" aria-label="Fechar" onClick={onClose}><X size={19} aria-hidden="true" /></button></div>
    <p id="confirm-lab-description">{description}</p>
    {error && <p className="dialog-error" role="alert">{error}</p>}
    <div className="dialog-actions"><button data-cancel className="button secondary" type="button" onClick={onClose}>Cancelar</button><button className="button danger" type="button" disabled={busy} onClick={onConfirm}>{busy ? 'Aguarde…' : removing ? 'Remover participação' : 'Sair do laboratório'}</button></div>
  </dialog>;
}

export function Laboratories({ user, onLogout, onUserUpdate, renderSlides, api = contagemApi }: {
  user: UsuarioResponseDTO;
  onLogout: () => void;
  onUserUpdate: (user: UsuarioResponseDTO) => void;
  renderSlides: (lab: LaboratorioResponseDTO | null, onBack: () => void, readOnly: boolean) => React.ReactNode;
  api?: ContagemApi;
}) {
  const [preferences, setPreferences] = useState(initialPreferences.value);
  const [labs, setLabs] = useState<LaboratorioResponseDTO[]>([]);
  const [listStatus, setListStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [listError, setListError] = useState('');
  const [view, setView] = useState<View>(() => window.location.pathname === '/perfil' ? 'profile' : 'list');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [currentLab, setCurrentLab] = useState<LaboratorioResponseDTO | null>(null);
  const [members, setMembers] = useState<ParticipacaoResponseDTO[]>([]);
  const [detailStatus, setDetailStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [detailRetry, setDetailRetry] = useState(0);
  const [slidesLab, setSlidesLab] = useState<LaboratorioResponseDTO | null>(null);
  const [slidesStatus, setSlidesStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [slidesRetry, setSlidesRetry] = useState(0);
  const [migrationTick, setMigrationTick] = useState(0);
  const [section, setSection] = useState<'members' | 'orientation' | 'settings' | 'invites'>('members');
  const [query, setQuery] = useState('');
  const [memberQuery, setMemberQuery] = useState('');
  const [pageError, setPageError] = useState('');
  const [notice, setNotice] = useState('');
  const [nameDraft, setNameDraft] = useState('');
  const [createName, setCreateName] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [savingName, setSavingName] = useState(false);
  const [editingParticipation, setEditingParticipation] = useState<string | null>(null);
  const [rolesDraft, setRolesDraft] = useState<PapelLaboratorio[]>(['MEMBRO']);
  const [savingRoles, setSavingRoles] = useState(false);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [confirming, setConfirming] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    document.documentElement.dataset.appearance = preferences.dark ? 'dark' : 'light';
    return () => { delete document.documentElement.dataset.appearance; };
  }, [preferences.dark]);

  useEffect(() => {
    setView(location.pathname === '/perfil' ? 'profile' : 'list');
    setPageError('');
    const navigationState = location.state as { notice?: unknown } | null;
    setNotice(typeof navigationState?.notice === 'string' ? navigationState.notice : '');
  }, [location.pathname]);

  const loadLabs = useCallback(async (signal?: AbortSignal) => {
    setListStatus('loading');
    setListError('');
    try {
      const result = await laboratorioApi.listarMeus(signal);
      if (signal?.aborted) return;
      setLabs(result);
      setListStatus('ready');
    } catch (error) {
      if (signal?.aborted) return;
      if (isUnauthorized(error)) return;
      setListError(errorMessage(error, 'Não foi possível carregar seus laboratórios.'));
      setListStatus('error');
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void loadLabs(controller.signal);
    return () => controller.abort();
  }, [loadLabs]);

  // Lâminas guardadas só neste navegador sobem sozinhas, sem pedir nada; falhas ficam para a próxima abertura da lista.
  useEffect(() => {
    if (listStatus !== 'ready') return;
    void migrarLaminasLocais({ api, usuarioId: user.id, laboratorios: labs }).then(guardadas => {
      if (guardadas === 0) return;
      setNotice(guardadas === 1 ? '1 lâmina antiga foi guardada no sistema.' : `${guardadas} lâminas antigas foram guardadas no sistema.`);
      setMigrationTick(value => value + 1);
    });
  }, [listStatus, labs, api, user.id]);

  const returnToListForMissingLab = useCallback((message = 'Este laboratório não está mais disponível para sua participação.') => {
    setSelectedId(null);
    setCurrentLab(null);
    setSlidesLab(null);
    setView('list');
    setPageError('');
    setNotice(message);
    void loadLabs();
  }, [loadLabs]);

  useEffect(() => {
    if (view !== 'manage' || !selectedId) return;
    let active = true;
    setDetailStatus('loading');
    setPageError('');
    const controller = new AbortController();
    Promise.all([laboratorioApi.buscar(selectedId, controller.signal), laboratorioApi.listarParticipacoes(selectedId, controller.signal)])
      .then(([lab, participations]) => {
        if (!active) return;
        setCurrentLab(lab);
        setLabs(current => current.map(item => item.id === lab.id ? lab : item));
        setNameDraft(lab.nome);
        setMembers(participations);
        setDetailStatus('ready');
      })
      .catch(error => {
        if (!active || isUnauthorized(error)) return;
        if (isNotFound(error)) {
          returnToListForMissingLab();
          return;
        }
        setPageError(errorMessage(error, 'Não foi possível carregar os dados do laboratório.'));
        setDetailStatus('error');
      });
    return () => { active = false; controller.abort(); };
  }, [view, selectedId, detailRetry, returnToListForMissingLab]);

  useEffect(() => {
    if (view !== 'slides') return;
    if (!selectedId) {
      setSlidesLab(null);
      setSlidesStatus('ready');
      return;
    }
    let active = true;
    setSlidesStatus('loading');
    setPageError('');
    const controller = new AbortController();
    laboratorioApi.buscar(selectedId, controller.signal)
      .then(lab => {
        if (!active) return;
        setSlidesLab(lab);
        setLabs(current => current.map(item => item.id === lab.id ? lab : item));
        setSlidesStatus('ready');
      })
      .catch(error => {
        if (!active || isUnauthorized(error)) return;
        if (isNotFound(error)) {
          returnToListForMissingLab();
          return;
        }
        setPageError(errorMessage(error, 'Não foi possível abrir este laboratório.'));
        setSlidesStatus('error');
      });
    return () => { active = false; controller.abort(); };
  }, [view, selectedId, slidesRetry, returnToListForMissingLab]);

  function goList() {
    if (location.pathname === '/perfil') navigate('/laboratorios');
    setView('list');
    setSelectedId(null);
    setCurrentLab(null);
    setSlidesLab(null);
    setConfirmation(null);
    setEditingParticipation(null);
    setQuery('');
    setPageError('');
  }

  function openManage(lab: LaboratorioResponseDTO) {
    setSelectedId(lab.id);
    setCurrentLab(null);
    setMembers([]);
    setSection('members');
    setMemberQuery('');
    setPageError('');
    setNotice('');
    setView('manage');
  }

  function openSlides(lab: LaboratorioResponseDTO | null) {
    setSelectedId(lab?.id ?? null);
    setSlidesLab(null);
    setPageError('');
    setNotice('');
    setView('slides');
  }

  function updatePreferences(next: typeof preferences): boolean {
    if (initialPreferences.error) {
      setPageError(initialPreferences.error);
      return false;
    }
    try {
      savePreferences(next);
      setPreferences(next);
      return true;
    } catch {
      setPageError('Não foi possível salvar a preferência neste navegador.');
      return false;
    }
  }

  async function createLaboratory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nome = createName.trim();
    if (!nome || creating) return;
    setCreating(true);
    setNotice('');
    setPageError('');
    try {
      const lab = await laboratorioApi.criar(nome);
      setLabs(current => [lab, ...current.filter(item => item.id !== lab.id)]);
      setCreateOpen(false);
      setCreateName('');
      openManage(lab);
      setNotice('Laboratório criado. Você recebeu os papéis Membro e Administrador.');
    } catch (error) {
      if (!isUnauthorized(error)) setPageError(errorMessage(error, 'Não foi possível criar o laboratório.'));
    } finally {
      setCreating(false);
    }
  }

  async function saveLaboratoryName(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!currentLab || currentLab.estado !== 'ATIVO' || !hasRole(currentLab.papeis, 'ADMIN') || savingName) return;
    const nome = nameDraft.trim();
    if (!nome) return;
    setSavingName(true);
    setNotice('');
    setPageError('');
    try {
      const updated = await laboratorioApi.atualizarNome(currentLab.id, nome);
      setCurrentLab(updated);
      setLabs(current => current.map(item => item.id === updated.id ? updated : item));
      setNameDraft(updated.nome);
      setNotice('Nome do laboratório atualizado.');
    } catch (error) {
      if (isNotFound(error)) returnToListForMissingLab();
      else if (isForbidden(error)) setPageError(`Somente administradores podem alterar o nome deste laboratório. ${errorMessage(error, '')}`.trim());
      else if (!isUnauthorized(error)) setPageError(errorMessage(error, 'Não foi possível alterar o nome do laboratório.'));
    } finally {
      setSavingName(false);
    }
  }

  function beginRoleEdit(participation: ParticipacaoResponseDTO) {
    setEditingParticipation(participation.id);
    setRolesDraft([...participation.papeis]);
    setPageError('');
  }

  async function reloadMembersAfterRoleConflict(
    participation: ParticipacaoResponseDTO,
    selectedRoles: PapelLaboratorio[],
    conflict: unknown,
  ) {
    if (!currentLab) return;
    try {
      const latest = await laboratorioApi.listarParticipacoes(currentLab.id);
      setMembers(latest);
      const updatedParticipation = latest.find(item => item.id === participation.id);
      if (!updatedParticipation) {
        setEditingParticipation(null);
        setPageError('A participação não está mais ativa. A lista de membros foi atualizada.');
        return;
      }
      if (updatedParticipation.versao !== participation.versao) {
        setEditingParticipation(null);
        setPageError('Os papéis foram alterados por outra pessoa. A lista foi atualizada; confira os valores antes de tentar novamente.');
        return;
      }
      if (hasRole(participation.papeis, 'ADMIN') && !hasRole(selectedRoles, 'ADMIN')
        && latest.filter(item => hasRole(item.papeis, 'ADMIN')).length <= 1) {
        setPageError(`${errorMessage(conflict, 'A operação não pode ser concluída.')} É necessário manter ao menos um Administrador ativo.`);
        return;
      }
      if (hasRole(participation.papeis, 'ORIENTADOR') && !hasRole(selectedRoles, 'ORIENTADOR')) {
        setPageError('Não foi possível remover o papel Orientador. Encerre todos os vínculos ativos dessa pessoa antes de tentar novamente.');
        return;
      }
      setPageError(errorMessage(conflict, 'A operação não pode ser concluída.'));
    } catch (error) {
      if (isNotFound(error)) returnToListForMissingLab();
      else if (!isUnauthorized(error)) setPageError(errorMessage(error, 'Não foi possível atualizar a lista de membros.'));
    }
  }

  async function saveRoles(event: FormEvent<HTMLFormElement>, participation: ParticipacaoResponseDTO) {
    event.preventDefault();
    if (!currentLab || currentLab.estado !== 'ATIVO' || !hasRole(currentLab.papeis, 'ADMIN') || savingRoles) return;
    const papeis = [...new Set([...rolesDraft, 'MEMBRO' as const])];
    setSavingRoles(true);
    setNotice('');
    setPageError('');
    try {
      const updated = await laboratorioApi.alterarPapeis(currentLab.id, participation.id, participation.versao, papeis);
      setMembers(current => current.map(item => item.id === updated.id ? updated : item));
      setEditingParticipation(null);
      setNotice(`Papéis de ${updated.nome} atualizados.`);
    } catch (error) {
      if (isNotFound(error)) returnToListForMissingLab();
      else if (isForbidden(error)) setPageError(`Somente administradores podem alterar papéis. ${errorMessage(error, '')}`.trim());
      else if (isConflict(error)) await reloadMembersAfterRoleConflict(participation, papeis, error);
      else if (!isUnauthorized(error)) setPageError(errorMessage(error, 'Não foi possível alterar os papéis.'));
    } finally {
      setSavingRoles(false);
    }
  }

  async function confirmMembershipChange() {
    if (!currentLab || currentLab.estado !== 'ATIVO' || !confirmation || confirming) return;
    const target = confirmation;
    setConfirming(true);
    setPageError('');
    try {
      if (target.kind === 'leave') {
        await laboratorioApi.sair(currentLab.id);
        setLabs(current => current.filter(item => item.id !== currentLab.id));
        setConfirmation(null);
        goList();
        setNotice(`Você saiu de ${currentLab.nome}.`);
        return;
      }
      await laboratorioApi.removerParticipacao(currentLab.id, target.participation.id);
      setMembers(current => current.filter(item => item.id !== target.participation.id));
      setConfirmation(null);
      setNotice(`${target.participation.nome} foi removido do laboratório.`);
    } catch (error) {
      if (isNotFound(error)) {
        setConfirmation(null);
        returnToListForMissingLab();
      } else if (isForbidden(error)) {
        setConfirmation(null);
        setPageError(`Somente administradores podem remover participantes. ${errorMessage(error, '')}`.trim());
      } else if (isConflict(error)) {
        setConfirmation(null);
        if (target.kind === 'leave') {
          const latest = await laboratorioApi.listarParticipacoes(currentLab.id).catch(() => members);
          setMembers(latest);
          const self = latest.find(item => isCurrentUser(item, user.email));
          if (self && hasRole(self.papeis, 'ADMIN') && latest.filter(item => hasRole(item.papeis, 'ADMIN')).length <= 1) {
            setPageError(`${errorMessage(error, 'A operação não pode ser concluída.')} Você é o último Administrador ativo. Atribua esse papel a outra pessoa antes de sair.`);
          } else setPageError(errorMessage(error, 'A operação não pode ser concluída.'));
        } else {
          const latest = await laboratorioApi.listarParticipacoes(currentLab.id).catch(() => members);
          setMembers(latest);
          const removed = latest.find(item => item.id === target.participation.id);
          if (removed && hasRole(removed.papeis, 'ADMIN') && latest.filter(item => hasRole(item.papeis, 'ADMIN')).length <= 1) {
            setPageError(`${errorMessage(error, 'A operação não pode ser concluída.')} É necessário manter ao menos um Administrador ativo.`);
          } else setPageError(errorMessage(error, 'A operação não pode ser concluída.'));
        }
      } else if (!isUnauthorized(error)) {
        setConfirmation(null);
        setPageError(errorMessage(error, 'Não foi possível encerrar a participação.'));
      }
    } finally {
      setConfirming(false);
    }
  }

  const admin = currentLab?.estado === 'ATIVO' && hasRole(currentLab.papeis, 'ADMIN');
  const visibleLabs = labs.filter(lab => lab.nome.toLocaleLowerCase('pt-BR').includes(query.trim().toLocaleLowerCase('pt-BR')));
  const visibleMembers = members.filter(participation => `${participation.nome} ${participation.email ?? ''}`
    .toLocaleLowerCase('pt-BR').includes(memberQuery.trim().toLocaleLowerCase('pt-BR')));

  return <div className="labs-app">
    <header className="topbar"><div className="topbar-inner">
      <Link className="brand" to="/laboratorios"><span className="brand-mark"><Microscope size={21} aria-hidden="true" /></span><span>citogenética<small>Laboratório de Citogenética · UFLA</small></span></Link>
      <div className="lab-account"><NotificationCenter/><AccountMenu name={user.nome} email={user.email} photo={preferences.photo} dark={preferences.dark} platformAdmin={user.role.nome === 'ADMIN'}
        onProfile={() => { if (location.pathname !== '/perfil') navigate('/perfil'); setView('profile'); setPageError(''); setNotice(''); }}
        onAppearance={() => updatePreferences({ ...preferences, dark: !preferences.dark })}
        onLogout={onLogout} /></div>
    </div></header>

    <FloatingNotice message={notice} onClose={() => setNotice('')} />

    {view === 'slides' ? <>
      {pageError && <div className="page-container lab-alert error" role="alert">{pageError}<button type="button" aria-label="Dispensar erro" onClick={() => setPageError('')}><X size={16} aria-hidden="true" /></button></div>}
      {slidesStatus === 'loading' ? <main className="page-container slide-access-state" aria-live="polite"><p>Carregando laboratório…</p></main>
        : slidesStatus === 'error' ? <main className="page-container slide-access-state"><h1>Não foi possível abrir as lâminas</h1><p>{pageError}</p><button className="button secondary" type="button" onClick={() => setSlidesRetry(value => value + 1)}>Tentar de novo</button><button className="text-button" type="button" onClick={goList}>Meus laboratórios</button></main>
          : <Fragment key={migrationTick}>{renderSlides(slidesLab, goList, slidesLab?.estado === 'DESATIVADO')}</Fragment>}
    </> : <main className={`page-container labs-main${view === 'manage' ? ' laboratory-management-page' : ''}`}><div className="labs-content">
      {(pageError || (listStatus === 'error' && view === 'list')) && !createOpen && !confirmation && <div className="lab-alert error" role="alert"><span>{pageError || listError}</span><button type="button" aria-label="Dispensar erro" onClick={() => { setPageError(''); setListError(''); }}><X size={16} aria-hidden="true" /></button></div>}
      {view === 'profile' ? <ProfileSettings user={user} photo={preferences.photo} onBack={goList} onUserUpdate={onUserUpdate} onPhotoChange={photo => updatePreferences({ ...preferences, photo })} />
        : view === 'list' ? <>
          <div className="page-heading"><div><h1 className={typeClass('display')}>Meus laboratórios</h1><p>{listStatus === 'ready' && labs.length === 0 ? 'Aqui ficam os laboratórios de que você participa.' : 'Escolha onde você vai continuar sua pesquisa.'}</p></div><button className="button primary" type="button" onClick={() => { setCreateName(''); setPageError(''); setCreateOpen(true); }}><Plus size={17} aria-hidden="true" />Criar laboratório</button></div>
          <section className="laboratory-list-section" aria-labelledby="laboratory-list-title">
            <div className="section-heading"><h2 id="laboratory-list-title">Laboratórios</h2><label className="laboratory-search"><Search size={16} aria-hidden="true" /><span className="sr-only">Buscar laboratório</span><input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Buscar laboratório" /></label></div>
            {listStatus === 'loading' ? <div className="laboratory-loading" aria-live="polite"><p>Carregando laboratórios…</p><span /><span /><span /></div>
              : listStatus === 'error' ? <div className="laboratory-empty"><p>{listError}</p><button className="button secondary" type="button" onClick={() => void loadLabs()}>Tentar novamente</button></div>
                : visibleLabs.length ? <div className="laboratory-grid">{visibleLabs.map(lab => <article className="laboratory-card" key={lab.id}>
                  <div className="laboratory-card-heading"><h3>{lab.nome}</h3><span className={`laboratory-state ${lab.estado === 'ATIVO' ? 'is-active' : 'is-disabled'}`}>{lab.estado === 'ATIVO' ? 'Ativo' : 'Desativado'}</span></div>
                  <div className="laboratory-card-meta"><span>Seus papéis</span><div className="role-chips">{lab.papeis.map(role => <span className="role-chip" key={role}>{PAPEL_LABEL[role]}</span>)}</div></div>
                  <div className="laboratory-card-actions"><button className="text-button laboratory-card-open" type="button" onClick={() => openSlides(lab)}>Abrir lâminas <ArrowRight size={15} aria-hidden="true" /></button><button className="text-button laboratory-card-manage" type="button" onClick={() => openManage(lab)}>Gerenciar</button></div>
                </article>)}</div>
                  : <div className="laboratory-empty"><h3>{query ? 'Nenhum laboratório encontrado' : 'Nenhum laboratório ainda'}</h3><p>{query ? 'Tente outro nome.' : 'Você ainda não participa de nenhum laboratório. Se alguém vai te convidar, o convite chega por e-mail. Você também pode criar um laboratório ou usar lâminas avulsas.'}</p>{query && <button className="text-button" type="button" onClick={() => setQuery('')}>Limpar busca</button>}</div>}
          </section>
          <section className="loose-slides"><div><h2>Lâminas avulsas</h2><p>Lâminas que não pertencem a nenhum laboratório.</p></div><button className="text-button" type="button" onClick={() => openSlides(null)}>Abrir lâminas avulsas <ArrowRight size={16} aria-hidden="true" /></button></section>
        </> : <>
          <div className="breadcrumb"><button type="button" onClick={goList}>Meus laboratórios</button><ChevronRight size={14} aria-hidden="true" /><span>Gerenciar laboratório</span></div>
          {detailStatus === 'loading' ? <div className="laboratory-loading" aria-live="polite"><p>Carregando laboratório e membros…</p><span /><span /></div>
              : detailStatus === 'error' ? <div className="laboratory-empty"><p>{pageError || 'Não foi possível carregar os dados do laboratório.'}</p><button className="button secondary" type="button" onClick={() => setDetailRetry(value => value + 1)}>Tentar novamente</button></div>
              : currentLab && <>
                <div className="page-heading laboratory-detail-heading"><div><h1>{currentLab.nome}</h1><div className="laboratory-detail-meta"><span className={`laboratory-state ${currentLab.estado === 'ATIVO' ? 'is-active' : 'is-disabled'}`}>{currentLab.estado === 'ATIVO' ? 'Ativo' : 'Desativado'}</span>{currentLab.estado === 'DESATIVADO' && <span>Somente leitura</span>}</div></div><button className="button primary" type="button" onClick={() => openSlides(currentLab)}>Acessar lâminas <ArrowRight size={16} aria-hidden="true" /></button></div>
                <div className="laboratory-management-panel"><nav className="tabs laboratory-tabs" aria-label="Gestão do laboratório"><button type="button" className={section === 'members' ? 'selected' : ''} aria-current={section === 'members' ? 'page' : undefined} onClick={() => { setSection('members'); setEditingParticipation(null); }}>Membros <span>{members.length}</span></button><button type="button" className={section === 'orientation' ? 'selected' : ''} aria-current={section === 'orientation' ? 'page' : undefined} onClick={() => { setSection('orientation'); setEditingParticipation(null); }}>Orientação</button>{admin && <button type="button" className={section === 'invites' ? 'selected' : ''} aria-current={section === 'invites' ? 'page' : undefined} onClick={() => { setSection('invites'); setEditingParticipation(null); }}>Convites</button>}<button type="button" className={section === 'settings' ? 'selected' : ''} aria-current={section === 'settings' ? 'page' : undefined} onClick={() => { setSection('settings'); setEditingParticipation(null); }}>Configurações</button></nav>
                <TabTransition tab={section}>
                {section === 'members' ? <section className="members-section" aria-labelledby="members-title">
                  <div className="members-toolbar"><div><h2 id="members-title">Equipe do laboratório</h2></div><label className="members-search"><Search size={16} aria-hidden="true" /><span className="sr-only">Buscar membro por nome ou e-mail</span><input type="search" value={memberQuery} onChange={event => setMemberQuery(event.target.value)} placeholder="Buscar membro" /></label></div>
                  <div className="members-table-wrap"><table className="members-table"><thead><tr><th scope="col">Pessoa</th><th scope="col">Papéis</th>{admin && <th scope="col"><span className="sr-only">Ações</span></th>}</tr></thead><tbody>
                    {visibleMembers.map(participation => <MemberRows key={participation.id} participation={participation} isMe={isCurrentUser(participation, user.email)} admin={!!admin} readOnly={currentLab.estado === 'DESATIVADO'} editing={editingParticipation === participation.id} roles={rolesDraft} saving={savingRoles} onEdit={() => beginRoleEdit(participation)} onRoles={setRolesDraft} onSave={event => void saveRoles(event, participation)} onCancel={() => setEditingParticipation(null)} onRemove={() => setConfirmation({ kind: 'remove', participation })} />)}
                  </tbody></table>
                    {!visibleMembers.length && <div className="members-empty"><p>{memberQuery ? 'Nenhum membro encontrado.' : 'Não há membros ativos neste laboratório.'}</p>{memberQuery && <button className="text-button" type="button" onClick={() => setMemberQuery('')}>Limpar busca</button>}</div>}
                  </div>
                </section> : section === 'orientation' ? <LaboratoryOrientations onNotice={setNotice} laboratorioId={currentLab.id} userId={user.id} isOrientador={hasRole(currentLab.papeis, 'ORIENTADOR')} isAdmin={!!admin} laboratorioAtivo={currentLab.estado === 'ATIVO'} /> : section === 'invites' && admin ? <LaboratoryInvitations laboratorioId={currentLab.id} onNotice={setNotice} /> : <section className="laboratory-settings" aria-labelledby="laboratory-settings-title">
                  <div><h2 id="laboratory-settings-title">Dados do laboratório</h2><p>O laboratório faz parte da UFLA.</p></div>
                  {admin ? <form className="laboratory-name-form" onSubmit={event => void saveLaboratoryName(event)}><label htmlFor="laboratory-name">Nome do laboratório</label><input id="laboratory-name" required maxLength={120} value={nameDraft} onChange={event => setNameDraft(event.target.value)} /><button className="button primary" type="submit" disabled={!nameDraft.trim() || savingName}>{savingName ? 'Salvando…' : 'Salvar nome'}</button></form>
                    : <div className="laboratory-read-only-name"><span>Nome do laboratório</span><strong>{currentLab.nome}</strong>{currentLab.estado === 'DESATIVADO' && <p>Este laboratório está desativado e disponível somente para consulta.</p>}</div>}
                </section>}
                {section === 'settings' && currentLab.estado === 'ATIVO' && <section className="leave-laboratory" aria-labelledby="leave-title"><div><h2 id="leave-title">Sair do laboratório</h2><p>Você perderá o acesso a este laboratório.</p></div><button className="button secondary" type="button" onClick={() => setConfirmation({ kind: 'leave' })}><LogOut size={16} aria-hidden="true" />Sair do laboratório</button></section>}
                </TabTransition>
                </div>
              </>}
        </>}
    </div><footer className="labs-footer"><span>Laboratório de Citogenética · UFLA</span></footer></main>}

    {createOpen && <CreateLaboratoryDialog name={createName} error={pageError} busy={creating} onName={setCreateName} onClose={() => { setCreateOpen(false); setPageError(''); }} onSubmit={event => void createLaboratory(event)} />}
    {confirmation && <ConfirmationDialog confirmation={confirmation} busy={confirming} error={pageError} onClose={() => { setConfirmation(null); setPageError(''); }} onConfirm={() => void confirmMembershipChange()} />}
  </div>;
}

function MemberRows({ participation, isMe, admin, readOnly, editing, roles, saving, onEdit, onRoles, onSave, onCancel, onRemove }: {
  participation: ParticipacaoResponseDTO;
  isMe: boolean;
  admin: boolean;
  readOnly: boolean;
  editing: boolean;
  roles: PapelLaboratorio[];
  saving: boolean;
  onEdit: () => void;
  onRoles: (roles: PapelLaboratorio[]) => void;
  onSave: (event: FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
  onRemove: () => void;
}) {
  const canManage = admin && !readOnly;
  return <>
    <tr className="members-table-row">
      <td data-label="Nome"><div className="members-person"><span className="member-initial" aria-hidden="true">{participation.nome.charAt(0).toLocaleUpperCase('pt-BR')}</span><span><strong>{participation.nome}{isMe && <span className="member-self">Você</span>}</strong><small className="members-email">{participation.email || 'E-mail indisponível'}</small></span></div></td>
      <td data-label="Papéis"><div className="role-chips">{participation.papeis.map(role => <span className="role-chip" key={role}>{PAPEL_LABEL[role]}</span>)}</div></td>
      {admin && <td data-label="Ações" className="members-actions-cell">{canManage && <div className="members-actions"><button className="text-button" type="button" aria-label={`Alterar papéis de ${participation.nome}`} onClick={onEdit}>Alterar papéis</button>{!isMe && <button className="text-button danger-text" type="button" aria-label={`Remover ${participation.nome} do laboratório`} onClick={onRemove}>Remover</button>}</div>}</td>}
    </tr>
    {editing && canManage && <tr className="members-edit-row"><td colSpan={admin ? 3 : 2}><form className="inline-role-editor" onSubmit={onSave}><RoleFields value={roles} onChange={onRoles} /><div className="dialog-actions"><button className="button secondary" type="button" onClick={onCancel}>Cancelar</button><button className="button primary" type="submit" disabled={saving}>{saving ? 'Salvando…' : 'Salvar papéis'}</button></div></form></td></tr>}
  </>;
}
