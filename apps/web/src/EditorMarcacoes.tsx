import { useEffect, useId, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import { ArrowUpRight, Eraser, MousePointer2, Redo2, Trash2, Type, Undo2 } from 'lucide-react';
import type { AnotacaoFotoDTO } from './api/colaboracao-api';
import { FiltroSombra, Marcacao, corHex, escalaTexto, luminancia, medidas } from './FotoAnotada';
import { SeletorCor } from './SeletorCor';

type Ferramenta = 'MOVER' | 'SETA' | 'TEXTO';
type Ponto = { x: number; y: number };
type Arraste =
  | { tipo: 'nova-seta'; inicio: Ponto; ponta: Ponto }
  | { tipo: 'mover'; indice: number; origem: Ponto; antes: AnotacaoFotoDTO[] }
  | { tipo: 'ponta'; indice: number; qual: 1 | 2; antes: AnotacaoFotoDTO[] }
  | { tipo: 'tamanho'; indice: number; distancia: number; escala: number; antes: AnotacaoFotoDTO[] };
type TextoEmEdicao = { indice: number | null; x: number; y: number; valor: string };

const FERRAMENTAS: { id: Ferramenta; nome: string; icone: typeof Type; atalho: string }[] = [
  { id: 'MOVER', nome: 'Mover', icone: MousePointer2, atalho: 'V' },
  { id: 'SETA', nome: 'Seta', icone: ArrowUpRight, atalho: 'S' },
  { id: 'TEXTO', nome: 'Texto', icone: Type, atalho: 'T' },
];

const DICAS: Record<Ferramenta, string> = {
  MOVER: 'Clique numa marcação para selecionar e arraste para mover. Dois cliques num texto para editar.',
  SETA: 'Arraste na foto: a ponta da seta fica onde você soltar.',
  TEXTO: 'Clique na foto e digite. Enter confirma; clique num texto já colocado para editar.',
};

const limitar = (valor: number) => Math.min(1, Math.max(0, valor));
const TAMANHO_MINIMO = 0.4, TAMANHO_MAXIMO = 4;
const mesmaLista = (a: AnotacaoFotoDTO[], b: AnotacaoFotoDTO[]) => JSON.stringify(a) === JSON.stringify(b);

/** Caixa aproximada do texto (em pixels da foto), para a moldura de seleção e a alça de tamanho. */
function caixaTexto(item: Extract<AnotacaoFotoDTO, { tipo: 'TEXTO' }>, largura: number, altura: number, fonte: number) {
  const f = fonte * escalaTexto(item);
  const width = item.texto.length * f * 0.64 + f * 0.6, height = f * 1.44;
  return { x: item.x1 * largura - width / 2, y: item.y1 * altura - height / 2, width, height };
}

/** Editor de marcações por cima da foto: ferramentas, cor, seleção, mover, desfazer e refazer. */
export function EditorMarcacoes({ src, alt, inicial, aoMudar }: { src: string; alt: string; inicial: AnotacaoFotoDTO[]; aoMudar: (anotacoes: AnotacaoFotoDTO[]) => void }) {
  const [anotacoes, setAnotacoes] = useState(inicial);
  const [passado, setPassado] = useState<AnotacaoFotoDTO[][]>([]);
  const [futuro, setFuturo] = useState<AnotacaoFotoDTO[][]>([]);
  const [ferramenta, setFerramenta] = useState<Ferramenta>('SETA');
  const [cor, setCor] = useState('#ffd60a');
  const [selecionada, setSelecionada] = useState<number | null>(null);
  const [arraste, setArraste] = useState<Arraste | null>(null);
  const [texto, setTexto] = useState<TextoEmEdicao | null>(null);
  const [tamanho, setTamanho] = useState<{ largura: number; altura: number } | null>(null);
  const [fontePx, setFontePx] = useState(16);
  /** Tamanho do último texto redimensionado: os textos novos já nascem com ele. */
  const [escalaNova, setEscalaNova] = useState(1);
  const svgRef = useRef<SVGSVGElement>(null);
  const campoRef = useRef<HTMLInputElement>(null);
  const textoRef = useRef<TextoEmEdicao | null>(null);
  const antesDaCor = useRef<AnotacaoFotoDTO[] | null>(null);
  const aoMudarRef = useRef(aoMudar);
  aoMudarRef.current = aoMudar;
  textoRef.current = texto;
  const sombra = `sombra-editor-${useId().replace(/:/g, '')}`;

  useEffect(() => { aoMudarRef.current(anotacoes); }, [anotacoes]);
  useEffect(() => { if (texto) campoRef.current?.focus(); }, [texto?.indice, texto?.x, texto?.y]); // eslint-disable-line react-hooks/exhaustive-deps

  /** Troca a lista guardando a anterior para desfazer. */
  function registrar(novas: AnotacaoFotoDTO[], antes: AnotacaoFotoDTO[] = anotacoes) {
    if (mesmaLista(novas, antes)) { setAnotacoes(novas); return; }
    setPassado(lista => [...lista, antes].slice(-100));
    setFuturo([]);
    setAnotacoes(novas);
  }

  function desfazer() {
    if (!passado.length) return;
    setFuturo(lista => [anotacoes, ...lista]);
    setAnotacoes(passado[passado.length - 1]);
    setPassado(lista => lista.slice(0, -1));
    setSelecionada(null);
  }

  function refazer() {
    if (!futuro.length) return;
    setPassado(lista => [...lista, anotacoes]);
    setAnotacoes(futuro[0]);
    setFuturo(lista => lista.slice(1));
    setSelecionada(null);
  }

  function apagarSelecionada() {
    if (selecionada === null) return;
    registrar(anotacoes.filter((_, i) => i !== selecionada));
    setSelecionada(null);
  }

  function escolherFerramenta(id: Ferramenta) {
    confirmarTexto();
    setFerramenta(id);
    if (id !== 'MOVER') setSelecionada(null);
  }

  function mudarCor(hex: string, final: boolean) {
    setCor(hex);
    if (selecionada === null || !anotacoes[selecionada]) return;
    if (!antesDaCor.current) antesDaCor.current = anotacoes;
    const novas = anotacoes.map((item, i) => (i === selecionada ? { ...item, cor: hex } : item));
    if (final) { registrar(novas, antesDaCor.current); antesDaCor.current = null; } else setAnotacoes(novas);
  }

  function selecionar(indice: number | null) {
    setSelecionada(indice);
    if (indice !== null && anotacoes[indice]) setCor(corHex(anotacoes[indice].cor));
  }

  function ponto(event: { clientX: number; clientY: number }): Ponto {
    const caixa = svgRef.current!.getBoundingClientRect();
    return { x: limitar((event.clientX - caixa.left) / (caixa.width || 1)), y: limitar((event.clientY - caixa.top) / (caixa.height || 1)) };
  }

  function abrirTexto(edicao: TextoEmEdicao) {
    const caixa = svgRef.current?.getBoundingClientRect();
    const escala = edicao.indice !== null ? escalaTexto(anotacoes[edicao.indice]) : escalaNova;
    if (tamanho && caixa?.width) setFontePx(medidas(tamanho.largura, tamanho.altura).fonte * escala * (caixa.width / tamanho.largura));
    if (edicao.indice !== null) setCor(corHex(anotacoes[edicao.indice].cor));
    setSelecionada(edicao.indice);
    setTexto(edicao);
  }

  function confirmarTexto() {
    const atual = textoRef.current;
    if (!atual) return;
    textoRef.current = null;
    setTexto(null);
    const valor = atual.valor.trim().slice(0, 80);
    if (atual.indice === null) {
      if (!valor) return;
      registrar([...anotacoes, { tipo: 'TEXTO', x1: atual.x, y1: atual.y, texto: valor, cor, ...(escalaNova !== 1 ? { tamanho: escalaNova } : {}) }]);
      setSelecionada(anotacoes.length);
    } else if (!valor) {
      registrar(anotacoes.filter((_, i) => i !== atual.indice));
      setSelecionada(null);
    } else {
      registrar(anotacoes.map((item, i) => (i === atual.indice ? { ...item, texto: valor, cor } as AnotacaoFotoDTO : item)));
    }
  }

  function cancelarTexto() {
    textoRef.current = null;
    setTexto(null);
  }

  function capturar(event: ReactPointerEvent) {
    svgRef.current?.setPointerCapture?.(event.pointerId);
  }

  function aoPressionarFundo(event: ReactPointerEvent<SVGSVGElement>) {
    if (event.button > 0) return;
    if (textoRef.current) { event.preventDefault(); confirmarTexto(); return; }
    const p = ponto(event);
    setSelecionada(null);
    if (ferramenta === 'TEXTO') { event.preventDefault(); abrirTexto({ indice: null, x: p.x, y: p.y, valor: '' }); return; }
    if (ferramenta === 'SETA') { capturar(event); setArraste({ tipo: 'nova-seta', inicio: p, ponta: p }); }
  }

  function aoPressionarMarcacao(event: ReactPointerEvent, indice: number) {
    if (event.button > 0) return;
    event.stopPropagation();
    const item = anotacoes[indice];
    if (ferramenta === 'TEXTO' && item.tipo === 'TEXTO') { event.preventDefault(); confirmarTexto(); abrirTexto({ indice, x: item.x1, y: item.y1, valor: item.texto }); return; }
    if (textoRef.current) confirmarTexto();
    selecionar(indice);
    capturar(event);
    setArraste({ tipo: 'mover', indice, origem: ponto(event), antes: anotacoes });
  }

  function aoPressionarAlca(event: ReactPointerEvent, indice: number, qual: 1 | 2) {
    if (event.button > 0) return;
    event.stopPropagation();
    capturar(event);
    setArraste({ tipo: 'ponta', indice, qual, antes: anotacoes });
  }

  /** Distância em pixels da foto entre o ponto e o centro do texto. */
  function distanciaDoCentro(item: AnotacaoFotoDTO, p: Ponto) {
    const { largura, altura } = tamanho ?? { largura: 1, altura: 1 };
    return Math.max(1e-6, Math.hypot((p.x - item.x1) * largura, (p.y - item.y1) * altura));
  }

  function aoPressionarAlcaTamanho(event: ReactPointerEvent, indice: number) {
    if (event.button > 0) return;
    event.stopPropagation();
    capturar(event);
    const item = anotacoes[indice];
    setArraste({ tipo: 'tamanho', indice, distancia: distanciaDoCentro(item, ponto(event)), escala: escalaTexto(item), antes: anotacoes });
  }

  function aoMover(event: ReactPointerEvent<SVGSVGElement>) {
    if (!arraste) return;
    const p = ponto(event);
    if (arraste.tipo === 'nova-seta') { setArraste({ ...arraste, ponta: p }); return; }
    const original = arraste.antes[arraste.indice];
    if (arraste.tipo === 'tamanho' && original.tipo === 'TEXTO') {
      const escala = Math.round(Math.min(TAMANHO_MAXIMO, Math.max(TAMANHO_MINIMO, arraste.escala * distanciaDoCentro(original, p) / arraste.distancia)) * 100) / 100;
      setEscalaNova(escala);
      setAnotacoes(arraste.antes.map((item, i) => (i === arraste.indice ? { ...original, tamanho: escala } : item)));
      return;
    }
    if (arraste.tipo === 'ponta' && original.tipo === 'SETA') {
      const novo = arraste.qual === 1 ? { ...original, x1: p.x, y1: p.y } : { ...original, x2: p.x, y2: p.y };
      setAnotacoes(arraste.antes.map((item, i) => (i === arraste.indice ? novo : item)));
      return;
    }
    if (arraste.tipo === 'mover') {
      const xs = original.tipo === 'SETA' ? [original.x1, original.x2] : [original.x1];
      const ys = original.tipo === 'SETA' ? [original.y1, original.y2] : [original.y1];
      // O deslocamento é limitado para a marcação inteira continuar dentro da foto.
      const dx = Math.min(1 - Math.max(...xs), Math.max(-Math.min(...xs), p.x - arraste.origem.x));
      const dy = Math.min(1 - Math.max(...ys), Math.max(-Math.min(...ys), p.y - arraste.origem.y));
      const movido = original.tipo === 'SETA'
        ? { ...original, x1: original.x1 + dx, y1: original.y1 + dy, x2: original.x2 + dx, y2: original.y2 + dy }
        : { ...original, x1: original.x1 + dx, y1: original.y1 + dy };
      setAnotacoes(arraste.antes.map((item, i) => (i === arraste.indice ? movido : item)));
    }
  }

  function aoSoltar(event: ReactPointerEvent<SVGSVGElement>) {
    if (!arraste) return;
    setArraste(null);
    if (arraste.tipo !== 'nova-seta') { registrar(anotacoes, arraste.antes); return; }
    const fim = ponto(event);
    const { inicio } = arraste;
    // Um clique sem arrastar vira uma seta curta apontando para onde se clicou.
    const curto = Math.hypot(fim.x - inicio.x, fim.y - inicio.y) < 0.02;
    const origem = curto ? { x: Math.max(0, inicio.x - 0.08), y: Math.min(1, inicio.y + 0.06) } : inicio;
    registrar([...anotacoes, { tipo: 'SETA', x1: origem.x, y1: origem.y, x2: fim.x, y2: fim.y, cor }]);
    setSelecionada(anotacoes.length);
  }

  // Atalhos: Delete apaga, Ctrl+Z / Ctrl+Shift+Z, V/S/T trocam a ferramenta, Esc tira a seleção.
  const atalhos = useRef<(event: KeyboardEvent) => void>(() => {});
  atalhos.current = (event: KeyboardEvent) => {
    const alvo = event.target;
    if (alvo instanceof Element && alvo.closest('input, textarea, select, [role="slider"]')) return;
    const ctrl = event.ctrlKey || event.metaKey;
    if (ctrl && event.key.toLowerCase() === 'z') { event.preventDefault(); if (event.shiftKey) refazer(); else desfazer(); return; }
    if (ctrl && event.key.toLowerCase() === 'y') { event.preventDefault(); refazer(); return; }
    if ((event.key === 'Delete' || event.key === 'Backspace') && selecionada !== null) { event.preventDefault(); apagarSelecionada(); return; }
    if (event.key === 'Escape' && selecionada !== null) { event.preventDefault(); setSelecionada(null); return; }
    if (!ctrl && !event.altKey) {
      const atalho = FERRAMENTAS.find(item => item.atalho === event.key.toUpperCase());
      if (atalho) escolherFerramenta(atalho.id);
    }
  };
  useEffect(() => {
    const ouvir = (event: KeyboardEvent) => atalhos.current(event);
    window.addEventListener('keydown', ouvir);
    return () => window.removeEventListener('keydown', ouvir);
  }, []);

  const rascunho: AnotacaoFotoDTO | null = arraste?.tipo === 'nova-seta' ? { tipo: 'SETA', x1: arraste.inicio.x, y1: arraste.inicio.y, x2: arraste.ponta.x, y2: arraste.ponta.y, cor } : null;
  const interativa = (item: AnotacaoFotoDTO) => ferramenta === 'MOVER' || (ferramenta === 'TEXTO' && item.tipo === 'TEXTO');

  return <div className="editor-marcacoes">
    <div className="annotate-toolbar" role="toolbar" aria-label="Ferramentas de marcação">
      <div className="annotate-segmented" role="group" aria-label="Ferramenta">
        {FERRAMENTAS.map(({ id, nome, icone: Icone, atalho }) => <button key={id} type="button" aria-pressed={ferramenta === id} title={`${nome} (${atalho})`} onClick={() => escolherFerramenta(id)}>
          <Icone size={16} aria-hidden="true" /> {nome}
        </button>)}
      </div>
      <SeletorCor cor={cor} aoMudar={mudarCor} />
      <div className="annotate-actions" role="group" aria-label="Ações">
        <button type="button" className="annotate-icon" aria-label="Desfazer" title="Desfazer (Ctrl+Z)" disabled={!passado.length} onClick={desfazer}><Undo2 size={17} aria-hidden="true" /></button>
        <button type="button" className="annotate-icon" aria-label="Refazer" title="Refazer (Ctrl+Shift+Z)" disabled={!futuro.length} onClick={refazer}><Redo2 size={17} aria-hidden="true" /></button>
        <span className="annotate-divider" aria-hidden="true" />
        <button type="button" className="annotate-icon" aria-label="Apagar marcação selecionada" title="Apagar selecionada (Delete)" disabled={selecionada === null} onClick={apagarSelecionada}><Trash2 size={17} aria-hidden="true" /></button>
        <button type="button" className="annotate-icon" aria-label="Limpar todas as marcações" title="Limpar tudo" disabled={!anotacoes.length}
          onClick={() => { cancelarTexto(); registrar([]); setSelecionada(null); }}><Eraser size={17} aria-hidden="true" /></button>
      </div>
    </div>

    <div className="photo-stage editing">
      <span className={`foto-anotada foto-anotada-inteira editando ferramenta-${ferramenta.toLowerCase()}`}>
        <img src={src} alt={alt} draggable={false} onLoad={event => setTamanho({ largura: event.currentTarget.naturalWidth || 1, altura: event.currentTarget.naturalHeight || 1 })} />
        {tamanho && <svg ref={svgRef} viewBox={`0 0 ${tamanho.largura} ${tamanho.altura}`} preserveAspectRatio="none" role="application"
          aria-label={DICAS[ferramenta]} onPointerDown={aoPressionarFundo} onPointerMove={aoMover} onPointerUp={aoSoltar} onPointerCancel={aoSoltar}>
          <defs><FiltroSombra id={sombra} base={medidas(tamanho.largura, tamanho.altura).base} /></defs>
          {anotacoes.map((item, indice) => {
            const { base, fonte, alca } = medidas(tamanho.largura, tamanho.altura);
            const ativa = selecionada === indice && !texto;
            return <g key={indice} className={`marcacao${interativa(item) ? ' interativa' : ''}${ativa ? ' selecionada' : ''}`}
              onPointerDown={interativa(item) || ativa ? event => aoPressionarMarcacao(event, indice) : undefined}
              onDoubleClick={item.tipo === 'TEXTO' ? () => abrirTexto({ indice, x: item.x1, y: item.y1, valor: item.texto }) : undefined}>
              <Marcacao item={item} largura={tamanho.largura} altura={tamanho.altura} sombra={sombra} oculta={texto?.indice === indice}>
                {item.tipo === 'SETA'
                  ? <line className="marcacao-alvo" x1={item.x1 * tamanho.largura} y1={item.y1 * tamanho.altura} x2={item.x2 * tamanho.largura} y2={item.y2 * tamanho.altura} strokeWidth={base * 0.03} />
                  : <rect className={ativa ? 'marcacao-moldura' : 'marcacao-alvo'} {...caixaTexto(item, tamanho.largura, tamanho.altura, fonte)}
                      rx={fonte * 0.18} strokeWidth={base * 0.002} strokeDasharray={`${base * 0.006} ${base * 0.004}`} />}
              </Marcacao>
              {ativa && item.tipo === 'TEXTO' && (() => {
                const caixa = caixaTexto(item, tamanho.largura, tamanho.altura, fonte);
                return <circle className="marcacao-alca marcacao-alca-tamanho" cx={caixa.x + caixa.width} cy={caixa.y + caixa.height} r={alca} strokeWidth={base * 0.0025}
                  onPointerDown={event => aoPressionarAlcaTamanho(event, indice)}><title>Arraste para mudar o tamanho</title></circle>;
              })()}
              {ativa && item.tipo === 'SETA' && ([1, 2] as const).map(qual => <circle key={qual} className="marcacao-alca"
                cx={(qual === 1 ? item.x1 : item.x2) * tamanho.largura} cy={(qual === 1 ? item.y1 : item.y2) * tamanho.altura} r={alca} strokeWidth={base * 0.0025}
                onPointerDown={event => aoPressionarAlca(event, indice, qual)} />)}
            </g>;
          })}
          {rascunho && <Marcacao item={rascunho} largura={tamanho.largura} altura={tamanho.altura} sombra={sombra} />}
        </svg>}
        {texto && <input ref={campoRef} className="marcacao-campo" aria-label="Texto da marcação" placeholder="Digite…" maxLength={80} value={texto.valor}
          style={{ left: `${texto.x * 100}%`, top: `${texto.y * 100}%`, fontSize: `${fontePx}px`, color: cor, width: `${Math.max(4, texto.valor.length + 1.5)}ch`,
            textShadow: luminancia(cor) > 0.45 ? '0 0 2px rgba(0,0,0,.7), 0 1px 3px rgba(0,0,0,.5)' : '0 0 2px #fff, 0 1px 3px rgba(255,255,255,.7)' }}
          onChange={event => setTexto(atual => atual && { ...atual, valor: event.target.value })}
          onBlur={confirmarTexto}
          onKeyDown={event => {
            if (event.key === 'Enter') { event.preventDefault(); confirmarTexto(); }
            if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); cancelarTexto(); }
          }} />}
      </span>
    </div>
  </div>;
}
