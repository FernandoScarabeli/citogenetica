import { useId, useState, type ReactNode } from 'react';
import type { AnotacaoFotoDTO } from './api/colaboracao-api';

/** Nomes de cor da primeira versão das marcações; as novas guardam a cor em hex. */
const CORES_ANTIGAS: Record<string, string> = { azul: '#1b2a8a', amarelo: '#ffd60a', vermelho: '#e5383b', branco: '#ffffff', preto: '#111111' };

export const corHex = (cor: string) => CORES_ANTIGAS[cor] ?? (/^#[0-9a-f]{6}$/i.test(cor) ? cor.toLowerCase() : '#ffd60a');

/** Luminância relativa (0 a 1), para escolher o contorno que destaca a marcação. */
export function luminancia(hex: string): number {
  const canal = (i: number) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * canal(1) + 0.7152 * canal(3) + 0.0722 * canal(5);
}
const contorno = (hex: string) => (luminancia(hex) > 0.45 ? 'rgba(0,0,0,.5)' : 'rgba(255,255,255,.55)');

/** Tamanho do texto: o padrão vezes o `tamanho` da marcação (0,4 a 4). */
export const escalaTexto = (item: AnotacaoFotoDTO) => (item.tipo === 'TEXTO' && item.tamanho ? item.tamanho : 1);

/** Medidas das marcações a partir do maior lado da foto, para ficarem iguais em qualquer resolução. */
export const medidas = (largura: number, altura: number) => {
  const base = Math.max(largura, altura);
  return { base, fonte: base * 0.042, alca: base * 0.011 };
};

/** Contorno da seta como um polígono só: haste afinando para trás e ponta com entalhe. */
function caminhoSeta(x1: number, y1: number, x2: number, y2: number, base: number): string {
  const comprimento = Math.hypot(x2 - x1, y2 - y1);
  if (comprimento < 1) return '';
  const dx = (x2 - x1) / comprimento, dy = (y2 - y1) / comprimento;
  const nx = -dy, ny = dx;
  const ponta = Math.min(base * 0.045, comprimento * 0.55);
  const abertura = ponta * 0.5;
  const cauda = base * 0.0022, haste = base * 0.0065;
  const bx = x2 - dx * ponta, by = y2 - dy * ponta;
  const ex = x2 - dx * ponta * 0.72, ey = y2 - dy * ponta * 0.72;
  const p = (x: number, y: number) => `${x.toFixed(2)},${y.toFixed(2)}`;
  return `M${p(x1 + nx * cauda, y1 + ny * cauda)} L${p(ex + nx * haste, ey + ny * haste)} L${p(bx + nx * abertura, by + ny * abertura)} `
    + `L${p(x2, y2)} L${p(bx - nx * abertura, by - ny * abertura)} L${p(ex - nx * haste, ey - ny * haste)} L${p(x1 - nx * cauda, y1 - ny * cauda)} Z`;
}

/** Uma marcação desenhada no sistema de coordenadas da foto (pixels). */
export function Marcacao({ item, largura, altura, sombra, oculta, children }: {
  item: AnotacaoFotoDTO; largura: number; altura: number; sombra: string; oculta?: boolean; children?: ReactNode;
}) {
  const { base, fonte } = medidas(largura, altura);
  const cor = corHex(item.cor);
  const estilo = oculta ? { opacity: 0 } : undefined;
  if (item.tipo === 'SETA') {
    return <g style={estilo}>
      <path d={caminhoSeta(item.x1 * largura, item.y1 * altura, item.x2 * largura, item.y2 * altura, base)} fill={cor} stroke={contorno(cor)}
        strokeWidth={base * 0.0018} strokeLinejoin="round" paintOrder="stroke" filter={`url(#${sombra})`} />
      {children}
    </g>;
  }
  const tamanhoFonte = fonte * escalaTexto(item);
  return <g style={estilo}>
    <text x={item.x1 * largura} y={item.y1 * altura} fill={cor} stroke={contorno(cor)} strokeWidth={tamanhoFonte * 0.045} strokeLinejoin="round" paintOrder="stroke"
      fontSize={tamanhoFonte} fontWeight={650} fontFamily="Inter Variable, Inter, system-ui, sans-serif" textAnchor="middle" dominantBaseline="central"
      filter={`url(#${sombra})`}>{item.texto}</text>
    {children}
  </g>;
}

export function FiltroSombra({ id, base }: { id: string; base: number }) {
  return <filter id={id} x="-20%" y="-20%" width="140%" height="140%">
    <feDropShadow dx="0" dy={base * 0.001} stdDeviation={base * 0.0014} floodColor="#000" floodOpacity="0.3" />
  </filter>;
}

/**
 * Foto com as marcações por cima, só para ver. `modo="miniatura"` recorta o centro (como object-fit: cover) e as
 * marcações acompanham o recorte; `modo="inteira"` mostra a foto no tamanho dela, sem faixas pretas.
 */
export function FotoAnotada({ src, alt, anotacoes, modo }: { src: string; alt: string; anotacoes: AnotacaoFotoDTO[]; modo: 'miniatura' | 'inteira' }) {
  const [tamanho, setTamanho] = useState<{ largura: number; altura: number } | null>(null);
  const sombra = `sombra-${useId().replace(/:/g, '')}`;
  return <span className={`foto-anotada foto-anotada-${modo}`}>
    <img src={src} alt={alt} loading={modo === 'miniatura' ? 'lazy' : undefined} onLoad={event => setTamanho({ largura: event.currentTarget.naturalWidth || 1, altura: event.currentTarget.naturalHeight || 1 })} />
    {tamanho && anotacoes.length > 0 && <svg viewBox={`0 0 ${tamanho.largura} ${tamanho.altura}`} preserveAspectRatio={modo === 'miniatura' ? 'xMidYMid slice' : 'none'} aria-hidden="true">
      <defs><FiltroSombra id={sombra} base={medidas(tamanho.largura, tamanho.altura).base} /></defs>
      {anotacoes.map((item, indice) => <Marcacao key={indice} item={item} largura={tamanho.largura} altura={tamanho.altura} sombra={sombra} />)}
    </svg>}
  </span>;
}
