import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ChevronLeft, ChevronRight, Microscope, Search, ShieldAlert, X } from 'lucide-react';
import { Link, useNavigate } from 'react-router';
import { adminApi } from '../api/admin-api';
import type { AdminLaboratorioPageDTO, AdminLaboratorioResponseDTO } from '../api/contracts/admin';
import type { EstadoLaboratorio } from '../api/contracts/laboratorio';
import type { UsuarioResponseDTO } from '../api/contracts/usuario';
import { isConflict, isForbidden, isNotFound } from '../api/errors';
import { FloatingNotice } from '../components/ui/floating-notice';
import { SegmentedControl } from '../components/ui/segmented-control';
import { typeClass } from '../lib/type-scale';
import { loadPreferences, savePreferences } from '../preferences';
import { NotificationCenter } from '../NotificationCenter';
import { AccountMenu } from './AccountMenu';
import { useSession } from './SessionProvider';
import '../laboratories.css';
import './admin-laboratories.css';

const PAGE_SIZE = 10;
const FORBIDDEN_MESSAGE = 'Você não tem permissão para esta ação.';

type StateFilter = '' | EstadoLaboratorio;
type Action = { kind: 'desativar' | 'reativar'; laboratory: AdminLaboratorioResponseDTO };
type AdminApi = Pick<typeof adminApi, 'listarLaboratorios' | 'desativarLaboratorio' | 'reativarLaboratorio'>;

const FILTER_OPTIONS: Array<{ value: StateFilter; label: string }> = [
  { value: '', label: 'Todos' },
  { value: 'ATIVO', label: 'Ativos' },
  { value: 'DESATIVADO', label: 'Desativados' },
];

function actionErrorMessage(error: unknown, kind: Action['kind']): string {
  if (isForbidden(error)) return FORBIDDEN_MESSAGE;
  if (isNotFound(error)) return 'Este laboratório não existe mais. A lista foi atualizada.';
  if (isConflict(error) && kind === 'reativar') return 'Não é possível reativar um laboratório sem administrador ativo.';
  return error instanceof Error && error.message ? error.message : 'Não foi possível concluir a ação. Tente novamente.';
}

function AdminActionDialog({ action, busy, error, onClose, onConfirm }: {
  action: Action;
  busy: boolean;
  error: string;
  onClose: () => void;
  onConfirm: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const busyRef = useRef(busy);
  busyRef.current = busy;
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

  const deactivating = action.kind === 'desativar';
  const close = () => { if (!busyRef.current) onClose(); };
  return <dialog ref={dialog} className="laboratory-dialog" aria-labelledby="admin-action-title" aria-describedby="admin-action-description" onCancel={event => { event.preventDefault(); close(); }}>
    <div className="dialog-heading">
      <h2 id="admin-action-title">{deactivating ? 'Desativar' : 'Reativar'} {action.laboratory.nome}?</h2>
      <button className="icon-button" type="button" aria-label="Fechar" disabled={busy} onClick={close}><X size={19} aria-hidden="true" /></button>
    </div>
    <p id="admin-action-description">{deactivating
      ? 'Os dados ficam guardados e os membros continuam vendo o laboratório, mas ninguém poderá alterá-lo nem enviar convites enquanto estiver desativado.'
      : 'O laboratório volta a aceitar alterações e convites.'}</p>
    {error && <p className="dialog-error" role="alert">{error}</p>}
    <div className="dialog-actions">
      <button data-cancel className="button secondary" type="button" disabled={busy} onClick={close}>Cancelar</button>
      <button className={`button ${deactivating ? 'danger' : 'primary'}`} type="button" disabled={busy} onClick={onConfirm}>
        {busy ? 'Aguarde…' : deactivating ? 'Desativar laboratório' : 'Reativar laboratório'}
      </button>
    </div>
  </dialog>;
}

/** Mesmo cabeçalho das outras telas: sino de notificações e o menu do perfil (perfil, admin, modo escuro, sair). */
function AdminTopbar({ user, onLogout }: { user: UsuarioResponseDTO; onLogout: () => void }) {
  const navigate = useNavigate();
  const [preferences, setPreferences] = useState(() => loadPreferences().value);
  useEffect(() => {
    document.documentElement.dataset.appearance = preferences.dark ? 'dark' : 'light';
    return () => { delete document.documentElement.dataset.appearance; };
  }, [preferences.dark]);
  function toggleAppearance() {
    const next = { ...preferences, dark: !preferences.dark };
    try { savePreferences(next); } catch { /* sem armazenamento: vale só nesta visita */ }
    setPreferences(next);
  }
  return <header className="topbar"><div className="topbar-inner">
    <Link className="brand" to="/laboratorios"><span className="brand-mark"><Microscope size={21} aria-hidden="true" /></span><span>citogenética<small>Laboratório de Citogenética · UFLA</small></span></Link>
    <div className="lab-account"><NotificationCenter/><AccountMenu name={user.nome} email={user.email} photo={preferences.photo} dark={preferences.dark}
      platformAdmin={user.role.nome === 'ADMIN'} onProfile={() => navigate('/perfil')} onAppearance={toggleAppearance} onLogout={onLogout} /></div>
  </div></header>;
}

export function AdminLaboratories({ user, onLogout, api = adminApi }: {
  user: UsuarioResponseDTO;
  onLogout: () => void;
  api?: AdminApi;
}) {
  const isAdmin = user.role.nome === 'ADMIN';
  return <div className="labs-app">
    <AdminTopbar user={user} onLogout={onLogout} />
    {isAdmin ? <AdminLaboratoriesContent api={api} /> : <ForbiddenState />}
  </div>;
}

function ForbiddenState() {
  return <main className="page-container labs-main admin-forbidden" aria-labelledby="admin-forbidden-title">
    <ShieldAlert size={28} aria-hidden="true" />
    <h1 id="admin-forbidden-title">Sem permissão para acessar esta página</h1>
    <p>A administração da plataforma é restrita a administradores gerais.</p>
    <Link className="button primary" to="/laboratorios">Voltar para Meus laboratórios</Link>
  </main>;
}

function AdminLaboratoriesContent({ api }: { api: AdminApi }) {
  const [draft, setDraft] = useState('');
  const [filters, setFilters] = useState<{ nome: string; estado: StateFilter }>({ nome: '', estado: '' });
  const [page, setPage] = useState(0);
  // De onde as linhas novas entram: da direita em "Próxima", da esquerda em "Anterior", de baixo ao filtrar ou buscar.
  const direction = useRef<'next' | 'previous' | 'filter' | 'none'>('filter');
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<AdminLaboratorioPageDTO | null>(null);
  // Muda a cada resposta nova: as linhas animam quando os dados chegam, não no clique.
  const [resultVersion, setResultVersion] = useState(0);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error' | 'forbidden'>('loading');
  const [loadError, setLoadError] = useState('');
  const [action, setAction] = useState<Action | null>(null);
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState('');
  const [pageError, setPageError] = useState('');
  const [notice, setNotice] = useState('');
  const busyRef = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    setStatus('loading');
    api.listarLaboratorios({
      nome: filters.nome,
      ...(filters.estado ? { estado: filters.estado } : {}),
      page,
      size: PAGE_SIZE,
      signal: controller.signal,
    }).then(data => {
      if (controller.signal.aborted) return;
      // Depois de desativar/reativar a página pode ficar vazia: volta uma página.
      if (!data.content.length && page > 0) {
        setPage(page - 1);
        return;
      }
      setResult(data);
      setResultVersion(version => version + 1);
      setStatus('ready');
    }).catch((error: unknown) => {
      if (controller.signal.aborted) return;
      if (isForbidden(error)) {
        setStatus('forbidden');
        return;
      }
      setLoadError(error instanceof Error && error.message ? error.message : 'Não foi possível carregar os laboratórios.');
      setStatus('error');
    });
    return () => controller.abort();
  }, [api, filters, page, reload]);

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    direction.current = 'filter';
    setFilters(current => ({ ...current, nome: draft.trim() }));
    setPage(0);
  }

  function changeState(estado: StateFilter) {
    direction.current = 'filter';
    setFilters(current => ({ ...current, estado }));
    setPage(0);
  }

  function clearFilters() {
    direction.current = 'filter';
    setDraft('');
    setFilters({ nome: '', estado: '' });
    setPage(0);
  }

  async function confirmAction() {
    if (!action || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setDialogError('');
    try {
      if (action.kind === 'desativar') await api.desativarLaboratorio(action.laboratory.id);
      else await api.reativarLaboratorio(action.laboratory.id);
      setNotice(action.kind === 'desativar'
        ? `${action.laboratory.nome} foi desativado.`
        : `${action.laboratory.nome} foi reativado.`);
      setAction(null);
      direction.current = 'none';
      setReload(value => value + 1);
    } catch (error) {
      const message = actionErrorMessage(error, action.kind);
      if (isNotFound(error)) {
        setAction(null);
        setPageError(message);
        setReload(value => value + 1);
      } else {
        setDialogError(message);
      }
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }

  if (status === 'forbidden') return <ForbiddenState />;

  const filtering = !!filters.nome || !!filters.estado;
  const totalPages = result?.totalPages ?? 0;
  return <main className="page-container labs-main admin-labs" aria-labelledby="admin-labs-title">
    <FloatingNotice message={notice} onClose={() => setNotice('')} />
    <div className="labs-content">
      <Link className="admin-back" to="/laboratorios"><ChevronLeft size={15} aria-hidden="true" />Meus laboratórios</Link>
      <div className="page-heading"><div>
        <h1 id="admin-labs-title" className={typeClass('display')}>Laboratórios da plataforma</h1>
        <p>Veja quem administra cada laboratório e desative ou reative laboratórios. Dados de pesquisa não ficam disponíveis nesta área.</p>
      </div></div>
      {pageError && <div className="lab-alert error" role="alert"><span>{pageError}</span><button type="button" aria-label="Dispensar erro" onClick={() => setPageError('')}><X size={16} aria-hidden="true" /></button></div>}
      <section aria-labelledby="admin-list-title">
        <h2 id="admin-list-title" className="sr-only">Lista de laboratórios</h2>
        <div className="admin-filters">
          <form className="admin-search" role="search" onSubmit={submitSearch}>
            <label className="laboratory-search"><Search size={16} aria-hidden="true" /><span className="sr-only">Buscar laboratório por nome</span>
              <input type="search" value={draft} onChange={event => setDraft(event.target.value)} placeholder="Buscar por nome" /></label>
            <button className="button secondary" type="submit">Buscar</button>
          </form>
          <SegmentedControl className="admin-state-filter" label="Filtrar por estado" options={FILTER_OPTIONS} value={filters.estado} onChange={changeState} />
        </div>
        {status === 'loading' && !result ? <div className="laboratory-loading" aria-live="polite"><p>Carregando laboratórios…</p><span /><span /><span /></div>
          : status === 'error' ? <div className="laboratory-empty"><p>{loadError}</p><button className="button secondary" type="button" onClick={() => setReload(value => value + 1)}>Tentar de novo</button></div>
            : result && !result.content.length ? <div className="laboratory-empty">
              <h3>{filtering ? 'Nenhum resultado para a busca' : 'Nenhum laboratório cadastrado'}</h3>
              <p>{filtering ? 'Tente outro nome ou outro estado.' : 'Os laboratórios criados na plataforma aparecem aqui.'}</p>
              {filtering && <button className="text-button" type="button" onClick={clearFilters}>Limpar busca e filtro</button>}
            </div> : result && <div className={`admin-results${status === 'loading' ? ' is-loading' : ''}`} aria-busy={status === 'loading'}>
              <div className="admin-table-wrap"><table className="admin-table">
                <caption className="sr-only">Laboratórios da plataforma</caption>
                <thead><tr><th scope="col">Laboratório</th><th scope="col">Estado</th><th scope="col">Membros ativos</th><th scope="col">Administradores</th><th scope="col"><span className="sr-only">Ações</span></th></tr></thead>
                <tbody key={resultVersion} className={`admin-rows is-${direction.current}`}>{result.content.map((lab, index) => <tr key={lab.id} style={{ '--row': index } as React.CSSProperties}>
                  <td data-label="Laboratório"><strong>{lab.nome}</strong></td>
                  <td data-label="Estado"><span className={`laboratory-state ${lab.estado === 'ATIVO' ? 'is-active' : 'is-disabled'}`}>{lab.estado === 'ATIVO' ? 'Ativo' : 'Desativado'}</span></td>
                  <td data-label="Membros ativos" className="admin-count">{lab.quantidadeMembrosAtivos}</td>
                  <td data-label="Administradores">{lab.administradores.length
                    ? <ul className="admin-people">{lab.administradores.map(person => <li key={person.email}><span>{person.nome}</span><a href={`mailto:${person.email}`}>{person.email}</a></li>)}</ul>
                    : <span className="admin-none">Sem administrador ativo</span>}</td>
                  <td className="admin-actions"><button className={`button ${lab.estado === 'ATIVO' ? 'secondary' : 'primary'}`} type="button"
                    aria-label={`${lab.estado === 'ATIVO' ? 'Desativar' : 'Reativar'} ${lab.nome}`}
                    onClick={() => { setDialogError(''); setAction({ kind: lab.estado === 'ATIVO' ? 'desativar' : 'reativar', laboratory: lab }); }}>
                    {lab.estado === 'ATIVO' ? 'Desativar' : 'Reativar'}</button></td>
                </tr>)}</tbody>
              </table></div>
              <nav className="admin-pagination" aria-label="Paginação">
                <span>{result.totalElements} {result.totalElements === 1 ? 'laboratório' : 'laboratórios'} · página {page + 1} de {Math.max(totalPages, 1)}</span>
                <div>
                  <button className="button secondary" type="button" disabled={page === 0 || status === 'loading'} onClick={() => { direction.current = 'previous'; setPage(page - 1); }}><ChevronLeft size={16} aria-hidden="true" />Anterior</button>
                  <button className="button secondary" type="button" disabled={page + 1 >= totalPages || status === 'loading'} onClick={() => { direction.current = 'next'; setPage(page + 1); }}>Próxima<ChevronRight size={16} aria-hidden="true" /></button>
                </div>
              </nav>
            </div>}
      </section>
    </div>
    {action && <AdminActionDialog action={action} busy={busy} error={dialogError} onClose={() => { setAction(null); setDialogError(''); }} onConfirm={() => void confirmAction()} />}
  </main>;
}

export function AdminLaboratoriesPage() {
  const { user, logout } = useSession();
  if (!user) return null;
  return <AdminLaboratories user={user} onLogout={() => void logout()} />;
}
