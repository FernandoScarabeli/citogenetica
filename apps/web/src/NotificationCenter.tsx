import { useCallback, useEffect, useRef, useState } from 'react';
import { Bell, Check, RefreshCw } from 'lucide-react';
import { isUnauthorized } from './api/errors';
import { notificacaoApi, type createNotificacaoApi } from './api/notificacao-api';
import type { NotificacaoResponseDTO } from './api/contracts/notificacao';
import './notifications.css';

type NotificationDataSource = Pick<ReturnType<typeof createNotificacaoApi>,
  'listar' | 'contarNaoLidas' | 'marcarLida' | 'marcarTodasLidas' | 'subscribeChanges'>;

function dateLabel(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(date);
}

function inviteNotification(item: NotificacaoResponseDTO): boolean {
  return item.titulo.toLocaleLowerCase('pt-BR').includes('convite');
}

export function NotificationCenter({ api = notificacaoApi }: { api?: NotificationDataSource }) {
  const [open, setOpen] = useState(false);
  const [count, setCount] = useState(0);
  const [countError, setCountError] = useState(false);
  const [items, setItems] = useState<NotificacaoResponseDTO[]>([]);
  const [listStatus, setListStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle');
  const [listError, setListError] = useState(false);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [savingAll, setSavingAll] = useState(false);
  const [actionError, setActionError] = useState('');
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const listRequest = useRef(0);
  const countRequest = useRef(0);
  const loadingMoreRef = useRef(false);

  const refreshCount = useCallback(async () => {
    const request = ++countRequest.current;
    try {
      const result = await api.contarNaoLidas();
      if (request !== countRequest.current) return;
      setCount(result.count);
      setCountError(false);
    } catch (error) {
      if (request !== countRequest.current || isUnauthorized(error)) return;
      setCountError(true);
    }
  }, [api]);

  const loadPage = useCallback(async (targetPage: number, replace: boolean) => {
    if (!replace && loadingMoreRef.current) return;
    const request = ++listRequest.current;
    if (replace) {
      loadingMoreRef.current = false;
      setLoadingMore(false);
      setListStatus('loading');
      setListError(false);
    } else {
      loadingMoreRef.current = true;
      setLoadingMore(true);
    }
    setActionError('');
    try {
      const result = await api.listar({ page: targetPage, size: 20 });
      if (request !== listRequest.current) return;
      setItems(current => {
        const combined = replace ? result.content : [...current, ...result.content];
        const unique = new Map<number, NotificacaoResponseDTO>();
        combined.forEach(item => unique.set(item.id, item));
        return [...unique.values()];
      });
      setPage(result.number);
      setHasMore(!result.last);
      setListStatus('ready');
      setListError(false);
    } catch (error) {
      if (request !== listRequest.current || isUnauthorized(error)) return;
      setListStatus('error');
      setListError(true);
    } finally {
      if (request === listRequest.current) {
        loadingMoreRef.current = false;
        setLoadingMore(false);
      }
    }
  }, [api]);

  const refreshPanel = useCallback(() => {
    void refreshCount();
    if (open) void loadPage(0, true);
  }, [loadPage, open, refreshCount]);

  const handleInvalidation = useCallback(() => {
    void refreshCount();
    void loadPage(0, true);
  }, [loadPage, refreshCount]);

  useEffect(() => { void refreshCount(); }, [refreshCount]);

  useEffect(() => {
    if (open) {
      void refreshCount();
      void loadPage(0, true);
    }
  }, [loadPage, open, refreshCount]);

  useEffect(() => api.subscribeChanges(handleInvalidation), [api, handleInvalidation]);

  useEffect(() => {
    function refreshWhenVisible() {
      if (document.visibilityState !== 'hidden') refreshPanel();
    }
    window.addEventListener('focus', refreshWhenVisible);
    document.addEventListener('visibilitychange', refreshWhenVisible);
    return () => {
      window.removeEventListener('focus', refreshWhenVisible);
      document.removeEventListener('visibilitychange', refreshWhenVisible);
    };
  }, [refreshPanel]);

  useEffect(() => {
    if (!open) return;
    function onPointerDown(event: PointerEvent) {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false);
        trigger.current?.focus();
      }
    }
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  async function markRead(item: NotificacaoResponseDTO) {
    if (item.lida || savingId !== null || savingAll) return;
    setSavingId(item.id);
    setActionError('');
    try {
      const updated = await api.marcarLida(item.id);
      setItems(current => current.map(notification => notification.id === updated.id ? updated : notification));
      await refreshCount();
    } catch (error) {
      if (!isUnauthorized(error)) setActionError('Não foi possível marcar esta notificação como lida. Tente novamente.');
    } finally {
      setSavingId(null);
    }
  }

  async function markAllRead() {
    if (count === 0 || savingAll || savingId !== null) return;
    setSavingAll(true);
    setActionError('');
    try {
      await api.marcarTodasLidas();
      setItems(current => current.map(item => ({ ...item, lida: true })));
      await refreshCount();
    } catch (error) {
      if (!isUnauthorized(error)) setActionError('Não foi possível marcar todas as notificações como lidas. Tente novamente.');
    } finally {
      setSavingAll(false);
    }
  }

  const countLabel = count === 1 ? '1 não lida' : `${count} não lidas`;
  const ariaLabel = `Notificações, ${countLabel}${countError ? ', contagem desatualizada' : ''}`;

  return <div className="notification-center" ref={root}>
    <span className="notification-sr-only" aria-live="polite" aria-atomic="true">{countLabel}</span>
    <button
      ref={trigger}
      className="notification-trigger"
      type="button"
      aria-label={ariaLabel}
      aria-expanded={open}
      aria-controls="notifications-panel"
      onClick={() => setOpen(value => !value)}
    >
      <Bell size={20} aria-hidden="true" />
      {count > 0 && <span className="notification-count" aria-hidden="true">{count > 99 ? '99+' : count}</span>}
    </button>
    {open && <section className="notifications-panel" id="notifications-panel" role="region" aria-labelledby="notifications-title">
      <div className="notifications-heading">
        <div>
          <h2 id="notifications-title">Notificações</h2>
          <p>{countLabel}</p>
        </div>
        {count > 0 && <button className="notification-text-action" type="button" disabled={savingAll || savingId !== null} onClick={() => void markAllRead()}>
          {savingAll ? 'Marcando…' : 'Marcar todas como lidas'}
        </button>}
      </div>
      {countError && <div className="notification-state notification-error" role="alert">
        <p>Não foi possível atualizar a contagem.</p>
        <button className="notification-text-action" type="button" onClick={() => void refreshCount()}>Tentar novamente</button>
      </div>}
      {actionError && <p className="notification-action-error" role="alert">{actionError}</p>}
      {listStatus === 'loading' && <p className="notification-state" role="status">Carregando notificações…</p>}
      {listStatus === 'error' && <div className="notification-state notification-error" role="alert">
        <p>Não foi possível carregar as notificações.</p>
        <button className="notification-text-action" type="button" onClick={() => { void refreshCount(); void loadPage(0, true); }}>Tentar novamente</button>
      </div>}
      {listStatus === 'ready' && items.length === 0 && <p className="notification-state">Nenhuma notificação por enquanto.</p>}
      {items.length > 0 && <ul className="notifications-list" aria-label="Lista de notificações">
        {items.map(item => <li className="notification-item" data-unread={!item.lida || undefined} key={item.id}>
          <div className="notification-copy">
            <h3>{item.titulo}</h3>
            <p>{item.mensagem}</p>
            {inviteNotification(item) && <p className="notification-invite-note">Para aceitar, use o link recebido por e-mail.</p>}
            <time dateTime={item.dataCriacao}>{dateLabel(item.dataCriacao)}</time>
          </div>
          {!item.lida && <button className="notification-read-action" type="button" disabled={savingId !== null || savingAll} onClick={() => void markRead(item)}>
            {savingId === item.id ? 'Marcando…' : <><Check size={15} aria-hidden="true" />Marcar como lida</>}
          </button>}
        </li>)}
      </ul>}
      {listStatus === 'ready' && hasMore && <button className="notification-load-more" type="button" disabled={loadingMore} onClick={() => void loadPage(page + 1, false)}>
        <RefreshCw size={15} aria-hidden="true" />{loadingMore ? 'Carregando…' : 'Carregar mais'}
      </button>}
      {loadingMore && <p className="notification-sr-only" role="status">Carregando mais notificações…</p>}
    </section>}
  </div>;
}
