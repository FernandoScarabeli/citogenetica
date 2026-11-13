import { useEffect, useRef, useState } from 'react';
import { ChevronDown, LogOut, Moon, ShieldCheck, UserRound } from 'lucide-react';
import { Link } from 'react-router';
import '../laboratories.css';

/** Menu do perfil no canto do cabeçalho: o mesmo em todas as telas da área logada (laboratórios, lâminas e admin). */
export function AccountMenu({ name, email, photo, dark, platformAdmin, onProfile, onAppearance, onLogout }: {
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
