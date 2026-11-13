import { useLayoutEffect, useRef, useState } from 'react';
import './segmented-control.css';

/**
 * Grupo de opções em que só uma fica marcada (botões com aria-pressed).
 * A marcação desliza com a receita "tab indicator com troca de cor" da skill animate: uma cópia dos rótulos já
 * pintada como selecionada fica por cima e é recortada (clip-path) só na opção marcada; ao trocar, o recorte
 * desliza e o fundo e a cor do texto mudam juntos, sem descompasso.
 */
export function SegmentedControl<T extends string>({ label, options, value, onChange, className = '' }: {
  label: string;
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  className?: string;
}) {
  const group = useRef<HTMLDivElement>(null);
  const [clip, setClip] = useState<string | null>(null);
  const [ready, setReady] = useState(false);

  useLayoutEffect(() => {
    const element = group.current;
    if (!element) return;
    const measure = () => {
      const selected = element.querySelector<HTMLElement>('button[aria-pressed="true"]');
      if (!selected) { setClip(null); return; }
      const right = element.clientWidth - selected.offsetLeft - selected.offsetWidth;
      setClip(`inset(0 ${right}px 0 ${selected.offsetLeft}px round 4px)`);
    };
    measure();
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure);
    observer?.observe(element);
    return () => observer?.disconnect();
  }, [value]);

  // A primeira posição aparece direto; só as trocas seguintes deslizam.
  useLayoutEffect(() => {
    if (!clip || ready) return;
    const frame = requestAnimationFrame(() => setReady(true));
    return () => cancelAnimationFrame(frame);
  }, [clip, ready]);

  return <div ref={group} className={`segmented ${className}`} role="group" aria-label={label}>
    {options.map(option => <button key={option.value} type="button" className="segmented-option"
      aria-pressed={option.value === value} onClick={() => onChange(option.value)}>{option.label}</button>)}
    {clip && <div className={`segmented-active${ready ? ' is-ready' : ''}`} aria-hidden="true" style={{ clipPath: clip }}>
      {options.map(option => <span key={option.value} className="segmented-option">{option.label}</span>)}
    </div>}
  </div>;
}
