import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Bell } from 'lucide-react';

/** Content and actions belong to the source of each notification. */
export type NotificationItem = { id: string; content: ReactNode };

export function Notifications({ items }: { items: NotificationItem[] }) {
 const [open, setOpen] = useState(false);
 const containerRef = useRef<HTMLDivElement>(null);
 const buttonRef = useRef<HTMLButtonElement>(null);
 useEffect(() => {
  if (!open) return;
  function onPointerDown(event: PointerEvent) {
   if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
  }
  function onKeyDown(event: KeyboardEvent) {
   if (event.key === 'Escape') { setOpen(false); buttonRef.current?.focus(); }
  }
  document.addEventListener('pointerdown', onPointerDown);
  document.addEventListener('keydown', onKeyDown);
  return () => { document.removeEventListener('pointerdown', onPointerDown); document.removeEventListener('keydown', onKeyDown); };
 }, [open]);
 return <div className="notifications" ref={containerRef}>
  <button ref={buttonRef} className="notification-trigger" type="button" aria-label={`Notificações${items.length ? `, ${items.length} pendente${items.length === 1 ? '' : 's'}` : ''}`} aria-expanded={open} aria-controls="notifications-panel" onClick={() => setOpen(value => !value)}>
   <Bell size={20}/>{items.length > 0 && <span className="notification-count" aria-hidden="true">{items.length}</span>}
  </button>
  {open && <div id="notifications-panel"><section className="notifications-panel" aria-labelledby="notifications-title">
   <div className="panel-heading"><h2 id="notifications-title">Notificações</h2></div>
   {items.length ? items.map(item => <div key={item.id}>{item.content}</div>) : <p className="lab-muted">Nenhuma notificação por enquanto.</p>}
  </section></div>}
 </div>;
}
