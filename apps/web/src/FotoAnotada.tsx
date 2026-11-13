import { useState, type PointerEvent as ReactPointerEvent } from 'react';
import type { AnotacaoFotoDTO, CorAnotacao } from './api/colaboracao-api';

export const CORES_ANOTACAO: { id: CorAnotacao; nome: string; hex: string }[] = [
  { id: 'azul', nome: 'Azul', hex: '#1b2a8a' },
  { id: 'amarelo', nome: 'Amarelo', hex: '#ffd60a' },
  { id: 'vermelho', nome: 'Vermelho', hex: '#e5383b' },
  { id: 'branco', nome: 'Branco', hex: '#ffffff' },
  { id: 'preto', nome: 'Preto', hex: '#111111' },
];
const hex = (cor: CorAnotacao) => CORES_ANOTACAO.find(item => item.id === cor)?.hex ?? '#1b2a8a';
/** Contorno de contraste para a marcação aparecer em fundo claro ou escuro. */
const contorno = (cor: CorAnotacao) => (cor === 'branco' || cor === 'amarelo' ? 'rgba(0,0,0,.55)' : 'rgba(255,255,255,.8)');

type Ponto = { x: number; y: number };

/** Desenho das marcações num SVG com o mesmo sistema de coordenadas da imagem (largura × altura em pixels). */
function Marcacoes({ anotacoes, largura, altura, rascunho }: { anotacoes: AnotacaoFotoDTO[]; largura: number; altura: number; rascunho?: AnotacaoFotoDTO | null }) {
  const base = Math.max(largura, altura);
  const traco = base * 0.007;
  const fonte = base * 0.06;
  const todas = rascunho ? [...anotacoes, rascunho] : anotacoes;
  return <>
    <defs>{CORES_ANOTACAO.map(cor => <marker key={cor.id} id={`ponta-${cor.id}`} viewBox="0 0 10 10" refX="7" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill={cor.hex} /></marker>)}</defs>
    {todas.map((item, indice) => item.tipo === 'SETA'
      ? <g key={indice}>
          <line x1={item.x1 * largura} y1={item.y1 * altura} x2={item.x2 * largura} y2={item.y2 * altura} stroke={contorno(item.cor)} strokeWidth={traco * 1.9} strokeLinecap="round" />
          <line x1={item.x1 * largura} y1={item.y1 * altura} x2={item.x2 * largura} y2={item.y2 * altura} stroke={hex(item.cor)} strokeWidth={traco} strokeLinecap="round" markerEnd={`url(#ponta-${item.cor})`} />
        </g>
      : <text key={indice} x={item.x1 * largura} y={item.y1 * altura} fill={hex(item.cor)} stroke={contorno(item.cor)} strokeWidth={fonte * 0.06} paintOrder="stroke" fontSize={fonte} fontWeight={700} fontFamily="Inter, system-ui, sans-serif" textAnchor="middle" dominantBaseline="middle">{item.texto}</text>)}
  </>;
}

/**
 * Foto com as marcações por cima. `modo="miniatura"` recorta o centro (como object-fit: cover) e as marcações
 * acompanham o recorte; `modo="inteira"` mostra a foto no tamanho dela, sem faixas pretas.
 */
export function FotoAnotada({ src, alt, anotacoes, modo, editor }: {
  src: string; alt: string; anotacoes: AnotacaoFotoDTO[]; modo: 'miniatura' | 'inteira';
  editor?: { ferramenta: 'SETA' | 'TEXTO'; cor: CorAnotacao; texto: string; aoAdicionar: (anotacao: AnotacaoFotoDTO) => void };
}) {
  const [tamanho, setTamanho] = useState<{ largura: number; altura: number } | null>(null);
  const [inicio, setInicio] = useState<Ponto | null>(null);
  const [ponta, setPonta] = useState<Ponto | null>(null);

  function ponto(event: ReactPointerEvent<SVGSVGElement>): Ponto {
    const caixa = event.currentTarget.getBoundingClientRect();
    const limitar = (valor: number) => Math.min(1, Math.max(0, valor));
    return { x: limitar((event.clientX - caixa.left) / (caixa.width || 1)), y: limitar((event.clientY - caixa.top) / (caixa.height || 1)) };
  }

  function aoPressionar(event: ReactPointerEvent<SVGSVGElement>) {
    if (!editor) return;
    const p = ponto(event);
    if (editor.ferramenta === 'TEXTO') {
      if (editor.texto.trim()) editor.aoAdicionar({ tipo: 'TEXTO', x1: p.x, y1: p.y, texto: editor.texto.trim(), cor: editor.cor });
      return;
    }
    event.currentTarget.setPointerCapture?.(event.pointerId);
    setInicio(p);
    setPonta(p);
  }

  function aoSoltar(event: ReactPointerEvent<SVGSVGElement>) {
    if (!editor || !inicio) return;
    const fim = ponto(event);
    // Um clique sem arrastar vira uma seta curta, apontando para onde se clicou.
    const curto = Math.hypot(fim.x - inicio.x, fim.y - inicio.y) < 0.02;
    const origem = curto ? { x: Math.max(0, inicio.x - 0.08), y: Math.min(1, inicio.y + 0.06) } : inicio;
    editor.aoAdicionar({ tipo: 'SETA', x1: origem.x, y1: origem.y, x2: fim.x, y2: fim.y, cor: editor.cor });
    setInicio(null);
    setPonta(null);
  }

  const rascunho: AnotacaoFotoDTO | null = editor && inicio && ponta ? { tipo: 'SETA', x1: inicio.x, y1: inicio.y, x2: ponta.x, y2: ponta.y, cor: editor.cor } : null;

  return <span className={`foto-anotada foto-anotada-${modo}${editor ? ' editando' : ''}`}>
    <img src={src} alt={alt} loading={modo === 'miniatura' ? 'lazy' : undefined} onLoad={event => setTamanho({ largura: event.currentTarget.naturalWidth || 1, altura: event.currentTarget.naturalHeight || 1 })} />
    {tamanho && (anotacoes.length > 0 || editor) && <svg
      viewBox={`0 0 ${tamanho.largura} ${tamanho.altura}`}
      preserveAspectRatio={modo === 'miniatura' ? 'xMidYMid slice' : 'none'}
      aria-hidden={editor ? undefined : true}
      role={editor ? 'application' : undefined}
      aria-label={editor ? (editor.ferramenta === 'SETA' ? 'Arraste na foto para desenhar a seta; a ponta fica onde soltar' : 'Clique na foto para colocar o texto') : undefined}
      onPointerDown={aoPressionar}
      onPointerMove={event => { if (inicio) setPonta(ponto(event)); }}
      onPointerUp={aoSoltar}
    ><Marcacoes anotacoes={anotacoes} largura={tamanho.largura} altura={tamanho.altura} rascunho={rascunho} /></svg>}
  </span>;
}
