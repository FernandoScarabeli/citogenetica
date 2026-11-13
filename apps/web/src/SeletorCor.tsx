import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent as ReactPointerEvent } from 'react';
import { ChevronDown } from 'lucide-react';

/** Cores prontas: as que mais aparecem em figuras de microscopia, com contraste em fundo claro e escuro. */
export const CORES_PRONTAS = [
  { hex: '#ffd60a', nome: 'Amarelo' }, { hex: '#ff7a00', nome: 'Laranja' }, { hex: '#e5383b', nome: 'Vermelho' },
  { hex: '#d63aff', nome: 'Magenta' }, { hex: '#00b4d8', nome: 'Ciano' }, { hex: '#2ecc71', nome: 'Verde' },
  { hex: '#1b2a8a', nome: 'Azul' }, { hex: '#ffffff', nome: 'Branco' }, { hex: '#111111', nome: 'Preto' },
];

type Hsv = { h: number; s: number; v: number };

function hexParaHsv(hex: string): Hsv {
  const r = parseInt(hex.slice(1, 3), 16) / 255, g = parseInt(hex.slice(3, 5), 16) / 255, b = parseInt(hex.slice(5, 7), 16) / 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
  let h = 0;
  if (d) h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return { h: (h * 60 + 360) % 360, s: max ? d / max : 0, v: max };
}

function hsvParaHex({ h, s, v }: Hsv): string {
  const f = (n: number) => {
    const k = (n + h / 60) % 6;
    return Math.round((v - v * s * Math.max(0, Math.min(k, 4 - k, 1))) * 255).toString(16).padStart(2, '0');
  };
  return `#${f(5)}${f(3)}${f(1)}`;
}

const limitar = (valor: number) => Math.min(1, Math.max(0, valor));

/**
 * Botão com a cor atual que abre o seletor: área de saturação e brilho, barra de matiz, campo hex e cores prontas.
 * `aoMudar(hex, final)` é chamado enquanto arrasta (final = false) e quando a escolha termina (final = true).
 */
export function SeletorCor({ cor, aoMudar }: { cor: string; aoMudar: (hex: string, final: boolean) => void }) {
  const [aberto, setAberto] = useState(false);
  const [hsv, setHsv] = useState<Hsv>(() => hexParaHsv(cor));
  const [campo, setCampo] = useState(cor.slice(1));
  const raiz = useRef<HTMLDivElement>(null);
  const botao = useRef<HTMLButtonElement>(null);

  // Cor trocada de fora (outra marcação selecionada): acompanha sem perder a matiz quando a cor é cinza.
  useEffect(() => {
    if (hsvParaHex(hsv) !== cor) {
      const novo = hexParaHsv(cor);
      setHsv(atual => (novo.s === 0 || novo.v === 0 ? { ...novo, h: atual.h } : novo));
    }
    setCampo(cor.slice(1));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cor]);

  useEffect(() => {
    if (!aberto) return;
    const fora = (event: PointerEvent) => { if (!raiz.current?.contains(event.target as Node)) setAberto(false); };
    document.addEventListener('pointerdown', fora);
    return () => document.removeEventListener('pointerdown', fora);
  }, [aberto]);

  function aplicar(novo: Hsv, final: boolean) {
    setHsv(novo);
    const hex = hsvParaHex(novo);
    setCampo(hex.slice(1));
    aoMudar(hex, final);
  }

  function arrastar(event: ReactPointerEvent<HTMLDivElement>, calcular: (x: number, y: number) => Hsv) {
    const alvo = event.currentTarget;
    const caixa = alvo.getBoundingClientRect();
    const em = (e: { clientX: number; clientY: number }) => calcular(limitar((e.clientX - caixa.left) / (caixa.width || 1)), limitar((e.clientY - caixa.top) / (caixa.height || 1)));
    alvo.setPointerCapture?.(event.pointerId);
    aplicar(em(event), false);
    const mover = (e: PointerEvent) => aplicar(em(e), false);
    const soltar = (e: PointerEvent) => {
      aplicar(em(e), true);
      alvo.removeEventListener('pointermove', mover);
      alvo.removeEventListener('pointerup', soltar);
    };
    alvo.addEventListener('pointermove', mover);
    alvo.addEventListener('pointerup', soltar);
  }

  function teclaArea(event: KeyboardEvent<HTMLDivElement>, eixo: 'sv' | 'h') {
    const passo = event.shiftKey ? 0.1 : 0.02;
    const delta: Record<string, [number, number]> = { ArrowLeft: [-passo, 0], ArrowRight: [passo, 0], ArrowUp: [0, passo], ArrowDown: [0, -passo] };
    const mudanca = delta[event.key];
    if (!mudanca) return;
    event.preventDefault();
    if (eixo === 'h') aplicar({ ...hsv, h: (hsv.h + (mudanca[0] || mudanca[1]) * 360 + 360) % 360 }, true);
    else aplicar({ ...hsv, s: limitar(hsv.s + mudanca[0]), v: limitar(hsv.v + mudanca[1]) }, true);
  }

  function confirmarCampo() {
    const valor = campo.trim().replace(/^#/, '');
    const completo = /^[0-9a-f]{3}$/i.test(valor) ? valor.split('').map(c => c + c).join('') : valor;
    if (/^[0-9a-f]{6}$/i.test(completo)) aplicar(hexParaHsv(`#${completo.toLowerCase()}`), true);
    else setCampo(cor.slice(1));
  }

  function fechar() {
    setAberto(false);
    botao.current?.focus();
  }

  const matiz = hsvParaHex({ h: hsv.h, s: 1, v: 1 });
  return <div className="seletor-cor" ref={raiz}>
    <button ref={botao} type="button" className="seletor-cor-botao" aria-haspopup="dialog" aria-expanded={aberto} onClick={() => setAberto(valor => !valor)}>
      <span className="seletor-cor-amostra" style={{ background: cor }} aria-hidden="true" />
      Cor<span className="sr-only">: {cor}</span>
      <ChevronDown size={14} aria-hidden="true" />
    </button>
    {aberto && <div className="seletor-cor-painel" role="dialog" aria-label="Escolher cor"
      onKeyDown={event => { if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); fechar(); } }}>
      <div className="seletor-cor-sv" style={{ backgroundColor: matiz }} role="slider" tabIndex={0} aria-label="Saturação e brilho"
        aria-valuenow={Math.round(hsv.s * 100)} aria-valuetext={`saturação ${Math.round(hsv.s * 100)}%, brilho ${Math.round(hsv.v * 100)}%`}
        onPointerDown={event => arrastar(event, (x, y) => ({ ...hsv, s: x, v: 1 - y }))} onKeyDown={event => teclaArea(event, 'sv')}>
        <span className="seletor-cor-cursor" style={{ left: `${hsv.s * 100}%`, top: `${(1 - hsv.v) * 100}%`, background: cor }} />
      </div>
      <div className="seletor-cor-matiz" role="slider" tabIndex={0} aria-label="Matiz" aria-valuemin={0} aria-valuemax={360} aria-valuenow={Math.round(hsv.h)}
        onPointerDown={event => arrastar(event, x => ({ ...hsv, h: x * 359.9 }))} onKeyDown={event => teclaArea(event, 'h')}>
        <span className="seletor-cor-cursor" style={{ left: `${(hsv.h / 360) * 100}%`, top: '50%', background: matiz }} />
      </div>
      <label className="seletor-cor-hex">
        <span className="seletor-cor-amostra grande" style={{ background: cor }} aria-hidden="true" />
        <span className="sr-only">Cor em hex</span>
        <span aria-hidden="true">#</span>
        <input value={campo} maxLength={7} spellCheck={false} autoComplete="off" onChange={event => setCampo(event.target.value)} onBlur={confirmarCampo}
          onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); confirmarCampo(); } }} />
      </label>
      <div className="seletor-cor-prontas" role="group" aria-label="Cores prontas">
        {CORES_PRONTAS.map(item => <button key={item.hex} type="button" aria-label={item.nome} title={item.nome} aria-pressed={cor === item.hex}
          style={{ background: item.hex }} onClick={() => aplicar(hexParaHsv(item.hex), true)} />)}
      </div>
    </div>}
  </div>;
}
