import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import './tab-transition.css';

/**
 * Conteúdo da aba. Trocar de aba é ocasional, então anima — mas sem sobrepor: o conteúdo antigo sai na hora
 * e o novo entra com opacidade e 8px vindo do lado da aba escolhida (direita se avançou, esquerda se voltou),
 * para mostrar para onde a pessoa foi. Só CSS (transform + opacity), sem biblioteca.
 */
export function TabTransition({ tab, index, children }: { tab: string; index: number; children: ReactNode }) {
  const previous = useRef(index);
  const direction = useRef<'forward' | 'back' | 'none'>('none');
  if (previous.current !== index) {
    direction.current = index > previous.current ? 'forward' : 'back';
    previous.current = index;
  }
  return <div className="laboratory-tab-content">
    <div key={tab} className={`tab-surface is-${direction.current}`}>{children}</div>
  </div>;
}

/**
 * Linha embaixo da aba ativa que desliza até a aba escolhida. Fica dentro do `nav` das abas e mede o botão marcado
 * com `.selected`; posiciona a linha com transform (translateX + scaleX), que não recalcula o layout a cada quadro.
 * Mede pelo próprio elemento (parentElement) porque, na montagem, o efeito do filho roda antes da ref do pai existir.
 */
export function TabIndicator({ active }: { active: string }) {
  const self = useRef<HTMLSpanElement>(null);
  const [box, setBox] = useState<{ left: number; width: number } | null>(null);
  const [ready, setReady] = useState(false);

  useLayoutEffect(() => {
    const nav = self.current?.parentElement;
    if (!nav) return;
    const measure = () => {
      const selected = nav.querySelector<HTMLElement>('button.selected');
      setBox(selected ? { left: selected.offsetLeft, width: selected.offsetWidth } : null);
    };
    measure();
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure);
    observer?.observe(nav);
    return () => observer?.disconnect();
  }, [active]);

  // A primeira posição aparece direto; só as trocas seguintes deslizam.
  useLayoutEffect(() => {
    if (!box || ready) return;
    const frame = requestAnimationFrame(() => setReady(true));
    return () => cancelAnimationFrame(frame);
  }, [box, ready]);

  return <span ref={self} className={`tab-indicator${ready ? ' is-ready' : ''}`} aria-hidden="true"
    style={box ? { transform: `translateX(${box.left}px) scaleX(${box.width})` } : { visibility: 'hidden' }} />;
}
