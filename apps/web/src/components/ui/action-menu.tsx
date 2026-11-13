import { forwardRef, useEffect, useId, useImperativeHandle, useRef, useState, type ComponentType, type KeyboardEvent } from 'react';
import { ChevronDown } from 'lucide-react';
import './action-menu.css';

type Icone = ComponentType<{ size?: number; 'aria-hidden'?: boolean | 'true' }>;

export type ItemMenu =
  | { tipo: 'titulo'; texto: string }
  | { tipo: 'acao'; id: string; rotulo: string; Icone?: Icone; aoEscolher: () => void };

export interface ActionMenuHandle {
  /** Devolve o foco ao botão, por exemplo depois de fechar um diálogo aberto pelo menu. */
  focar: () => void;
}

/**
 * Botão que abre um menu flutuante de ações (padrão "menu button" da WAI-ARIA):
 * setas, Home/End e as iniciais navegam; Enter/Espaço escolhem; Esc e clique fora fecham e devolvem o foco ao botão.
 */
export const ActionMenu = forwardRef<ActionMenuHandle, {
  rotulo: string;
  Icone?: Icone;
  itens: ItemMenu[];
  /** Enquanto ocupado (por exemplo, baixando), o botão mostra este texto e não abre. */
  ocupado?: string | null;
  alinhar?: 'inicio' | 'fim';
}>(function ActionMenu({ rotulo, Icone, itens, ocupado = null, alinhar = 'fim' }, ref) {
  const [aberto, setAberto] = useState(false);
  const [ativo, setAtivo] = useState(0);
  const raiz = useRef<HTMLDivElement>(null);
  const botao = useRef<HTMLButtonElement>(null);
  const opcoes = useRef<(HTMLButtonElement | null)[]>([]);
  const id = useId();
  const acoes = itens.filter((item): item is Extract<ItemMenu, { tipo: 'acao' }> => item.tipo === 'acao');

  useImperativeHandle(ref, () => ({ focar: () => botao.current?.focus() }), []);

  useEffect(() => {
    if (!aberto) return;
    opcoes.current[ativo]?.focus();
  }, [aberto, ativo]);

  useEffect(() => {
    if (!aberto) return;
    function fora(event: PointerEvent) {
      if (!raiz.current?.contains(event.target as Node)) setAberto(false);
    }
    document.addEventListener('pointerdown', fora);
    return () => document.removeEventListener('pointerdown', fora);
  }, [aberto]);

  function abrir(indice = 0) {
    if (ocupado || acoes.length === 0) return;
    setAtivo(indice);
    setAberto(true);
  }

  function fechar(devolverFoco = true) {
    setAberto(false);
    if (devolverFoco) botao.current?.focus();
  }

  function escolher(indice: number) {
    fechar();
    acoes[indice].aoEscolher();
  }

  function teclaNoBotao(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      abrir(event.key === 'ArrowDown' ? 0 : acoes.length - 1);
    }
  }

  function teclaNoMenu(event: KeyboardEvent<HTMLDivElement>) {
    const total = acoes.length;
    const mover = (indice: number) => { event.preventDefault(); setAtivo((indice + total) % total); };
    if (event.key === 'ArrowDown') mover(ativo + 1);
    else if (event.key === 'ArrowUp') mover(ativo - 1);
    else if (event.key === 'Home') mover(0);
    else if (event.key === 'End') mover(total - 1);
    else if (event.key === 'Escape') { event.preventDefault(); fechar(); }
    else if (event.key === 'Tab') fechar(false);
    else if (event.key.length === 1 && /\S/.test(event.key)) {
      const letra = event.key.toLocaleLowerCase('pt-BR');
      const depois = [...acoes.slice(ativo + 1), ...acoes.slice(0, ativo + 1)];
      const achado = depois.find(item => item.rotulo.toLocaleLowerCase('pt-BR').startsWith(letra));
      if (achado) mover(acoes.indexOf(achado));
    }
  }

  let indiceAcao = -1;
  return <div className="action-menu" ref={raiz}>
    <button ref={botao} className="button secondary action-menu-trigger" type="button" id={`${id}-botao`}
      aria-haspopup="menu" aria-expanded={aberto} aria-controls={aberto ? `${id}-menu` : undefined}
      aria-busy={ocupado ? true : undefined} disabled={!!ocupado || acoes.length === 0}
      onClick={() => (aberto ? fechar() : abrir())} onKeyDown={teclaNoBotao}>
      {Icone && <Icone size={16} aria-hidden="true" />}{ocupado ?? rotulo}<ChevronDown className="action-menu-chevron" size={16} aria-hidden="true" />
    </button>
    {aberto && <div className={`action-menu-list${alinhar === 'inicio' ? ' is-start' : ''}`} id={`${id}-menu`} role="menu" aria-labelledby={`${id}-botao`} onKeyDown={teclaNoMenu}>
      {itens.map((item, posicao) => {
        if (item.tipo === 'titulo') return <div className="action-menu-heading" role="presentation" key={`t-${posicao}`}>{item.texto}</div>;
        const indice = ++indiceAcao;
        const { Icone: IconeItem } = item;
        return <button key={item.id} ref={elemento => { opcoes.current[indice] = elemento; }} className="action-menu-item" type="button" role="menuitem"
          tabIndex={indice === ativo ? 0 : -1} onClick={() => escolher(indice)} onPointerMove={() => setAtivo(indice)}>
          {IconeItem && <IconeItem size={16} aria-hidden="true" />}<span>{item.rotulo}</span>
        </button>;
      })}
    </div>}
  </div>;
});
