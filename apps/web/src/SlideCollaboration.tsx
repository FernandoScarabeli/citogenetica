import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ArrowUpRight, Eraser, ImagePlus, PenLine, Trash2, Type, Undo2 } from 'lucide-react';
import { typeClass } from '@/lib/type-scale';
import { colaboracaoApi, fotoUrl, type AnotacaoFotoDTO, type ColaboracaoApi, type ComentarioLaminaDTO, type CorAnotacao, type FotoLaminaDTO } from './api/colaboracao-api';
import { CORES_ANOTACAO, FotoAnotada } from './FotoAnotada';
import { ApiError, NetworkError, isUnauthorized } from './api/errors';
import { formatarData, formatarHora } from './contagem-utils';
import { Modal } from './SlideWidgets';

const TIPOS_ACEITOS = ['image/jpeg', 'image/png', 'image/webp'];
const TAMANHO_MAXIMO = 10 * 1024 * 1024;

function mensagem(error: unknown, padrao: string): string {
  if (error instanceof NetworkError) return 'Sem conexão. Tente de novo quando a internet voltar.';
  if (error instanceof ApiError && error.message) return error.message;
  return padrao;
}

const quando = (iso: string) => `${formatarData(iso)} às ${formatarHora(iso)}`;

/** Fotos da lâmina: galeria, envio com legenda e foto aberta em tamanho grande. */
export function SlidePhotos({ laminaId, canEdit, api = colaboracaoApi }: { laminaId: string; canEdit: boolean; api?: ColaboracaoApi }) {
  const [fotos, setFotos] = useState<FotoLaminaDTO[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [erro, setErro] = useState('');
  const [nova, setNova] = useState<{ arquivo: File; previa: string } | null>(null);
  const [legenda, setLegenda] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [aberta, setAberta] = useState<FotoLaminaDTO | null>(null);
  const [apagando, setApagando] = useState(false);
  const [edicao, setEdicao] = useState<{ legenda: string; anotacoes: AnotacaoFotoDTO[] } | null>(null);
  const [ferramenta, setFerramenta] = useState<'SETA' | 'TEXTO'>('SETA');
  const [cor, setCor] = useState<CorAnotacao>('azul');
  const [textoMarcacao, setTextoMarcacao] = useState('A');
  const [salvando, setSalvando] = useState(false);
  const arquivoRef = useRef<HTMLInputElement>(null);

  /** Próxima letra livre (A, B, C…) para o texto da marcação, como nas figuras de artigo. */
  const proximaLetra = (anotacoes: AnotacaoFotoDTO[]) => String.fromCharCode(65 + Math.min(25, anotacoes.filter(item => item.tipo === 'TEXTO').length));

  function abrir(foto: FotoLaminaDTO) {
    setErro('');
    setEdicao(null);
    setAberta(foto);
  }

  function comecarEdicao(foto: FotoLaminaDTO) {
    const anotacoes = foto.anotacoes ?? [];
    setErro('');
    setFerramenta('SETA');
    setTextoMarcacao(proximaLetra(anotacoes));
    setEdicao({ legenda: foto.legenda ?? '', anotacoes });
  }

  function adicionar(anotacao: AnotacaoFotoDTO) {
    setEdicao(atual => {
      if (!atual) return atual;
      const anotacoes = [...atual.anotacoes, anotacao];
      if (anotacao.tipo === 'TEXTO' && /^[A-Z]$/.test(anotacao.texto)) setTextoMarcacao(proximaLetra(anotacoes));
      return { ...atual, anotacoes };
    });
  }

  async function salvarEdicao() {
    if (!aberta || !edicao || salvando) return;
    setSalvando(true);
    setErro('');
    try {
      const atualizada = await api.editarFoto(laminaId, aberta.id, edicao);
      setFotos(atual => atual.map(item => item.id === atualizada.id ? atualizada : item));
      setAberta(atualizada);
      setEdicao(null);
    } catch (error) {
      if (!isUnauthorized(error)) setErro(mensagem(error, 'Não foi possível salvar as marcações. Tente de novo.'));
    } finally {
      setSalvando(false);
    }
  }

  useEffect(() => {
    const controller = new AbortController();
    setStatus('loading');
    api.listarFotos(laminaId, controller.signal)
      .then(lista => { setFotos(lista); setStatus('ready'); })
      .catch(error => { if (!controller.signal.aborted && !isUnauthorized(error)) setStatus('error'); });
    return () => controller.abort();
  }, [api, laminaId]);

  useEffect(() => () => { if (nova) URL.revokeObjectURL(nova.previa); }, [nova]);

  function escolher(arquivo: File | undefined) {
    if (!arquivo) return;
    if (!TIPOS_ACEITOS.includes(arquivo.type) || arquivo.size > TAMANHO_MAXIMO) {
      setErro('Escolha uma imagem JPG, PNG ou WebP de até 10 MB.');
      return;
    }
    setErro('');
    setLegenda('');
    setNova({ arquivo, previa: URL.createObjectURL(arquivo) });
  }

  async function enviar(event: FormEvent) {
    event.preventDefault();
    if (!nova || enviando) return;
    setEnviando(true);
    setErro('');
    try {
      const foto = await api.enviarFoto(laminaId, nova.arquivo, legenda);
      setFotos(atual => [...atual, foto]);
      setNova(null);
    } catch (error) {
      if (!isUnauthorized(error)) setErro(mensagem(error, 'Não foi possível enviar a foto. Tente de novo.'));
    } finally {
      setEnviando(false);
    }
  }

  async function apagar(foto: FotoLaminaDTO) {
    if (apagando) return;
    setApagando(true);
    try {
      await api.apagarFoto(laminaId, foto.id);
      setFotos(atual => atual.filter(item => item.id !== foto.id));
      setAberta(null);
    } catch (error) {
      if (!isUnauthorized(error)) setErro(mensagem(error, 'Não foi possível apagar a foto. Tente de novo.'));
    } finally {
      setApagando(false);
    }
  }

  return <section className="photos-section" aria-labelledby="fotos-titulo">
    <div className="collab-heading">
      <h2 id="fotos-titulo" className={typeClass('title')}>Fotos{fotos.length > 0 && <span className="count-badge">{fotos.length}</span>}</h2>
      {canEdit && <>
        <button className="button secondary" type="button" onClick={() => arquivoRef.current?.click()}><ImagePlus size={16} aria-hidden="true" /> Adicionar foto</button>
        <input ref={arquivoRef} className="sr-only" type="file" accept={TIPOS_ACEITOS.join(',')} aria-label="Escolher foto da lâmina" onChange={event => { escolher(event.target.files?.[0]); event.target.value = ''; }} />
      </>}
    </div>
    {erro && !nova && <p className="select-error" role="alert">{erro}</p>}
    {status === 'loading' ? <p className="collab-empty">Carregando as fotos…</p>
      : status === 'error' ? <p className="collab-empty" role="alert">Não foi possível carregar as fotos.</p>
      : fotos.length === 0 ? <p className="collab-empty">{canEdit ? 'Nenhuma foto ainda. Adicione fotos do microscópio para guardar junto com a contagem.' : 'Nenhuma foto nesta lâmina.'}</p>
      : <ul className="photo-grid" aria-label="Fotos da lâmina">{fotos.map(foto => <li key={foto.id}>
          <button type="button" className="photo-thumb" onClick={() => abrir(foto)} aria-label={`Abrir foto${foto.legenda ? `: ${foto.legenda}` : ''}`}>
            <FotoAnotada src={fotoUrl(laminaId, foto.id)} alt="" anotacoes={foto.anotacoes ?? []} modo="miniatura" />
          </button>
          {foto.legenda && <span className="photo-caption">{foto.legenda}</span>}
        </li>)}</ul>}

    {nova && <Modal title="Nova foto" onClose={() => { if (!enviando) setNova(null); }} locked={enviando}>
      <form onSubmit={event => void enviar(event)}>
        <img className="photo-preview" src={nova.previa} alt="Prévia da foto escolhida" />
        <label>Legenda <span>Opcional</span><input autoFocus maxLength={200} value={legenda} onChange={event => setLegenda(event.target.value)} placeholder="Ex.: metáfase com ponte" /></label>
        {erro && <p className="select-error" role="alert">{erro}</p>}
        <div className="modal-actions"><button className="button secondary" type="button" disabled={enviando} onClick={() => setNova(null)}>Cancelar</button><button className="button primary" disabled={enviando}>{enviando ? 'Enviando…' : 'Enviar foto'}</button></div>
      </form>
    </Modal>}

    {aberta && <Modal title={edicao ? 'Anotar foto' : aberta.legenda || 'Foto da lâmina'} className="photo-dialog" locked={salvando} onClose={() => { if (!salvando) { setAberta(null); setEdicao(null); } }}>
      {edicao && <div className="annotate-toolbar" role="toolbar" aria-label="Ferramentas de marcação">
        <div className="annotate-group" role="group" aria-label="Ferramenta">
          <button type="button" aria-pressed={ferramenta === 'SETA'} onClick={() => setFerramenta('SETA')}><ArrowUpRight size={16} aria-hidden="true" /> Seta</button>
          <button type="button" aria-pressed={ferramenta === 'TEXTO'} onClick={() => setFerramenta('TEXTO')}><Type size={16} aria-hidden="true" /> Texto</button>
        </div>
        {ferramenta === 'TEXTO' && <label className="annotate-text">Texto <input value={textoMarcacao} maxLength={80} onChange={event => setTextoMarcacao(event.target.value)} /></label>}
        <div className="annotate-group annotate-colors" role="group" aria-label="Cor">
          {CORES_ANOTACAO.map(item => <button key={item.id} type="button" className="annotate-swatch" aria-pressed={cor === item.id} aria-label={item.nome} title={item.nome} style={{ background: item.hex }} onClick={() => setCor(item.id)} />)}
        </div>
        <div className="annotate-group">
          <button type="button" disabled={edicao.anotacoes.length === 0} onClick={() => setEdicao(atual => atual && { ...atual, anotacoes: atual.anotacoes.slice(0, -1) })}><Undo2 size={16} aria-hidden="true" /> Desfazer</button>
          <button type="button" disabled={edicao.anotacoes.length === 0} onClick={() => { setEdicao(atual => atual && { ...atual, anotacoes: [] }); setTextoMarcacao('A'); }}><Eraser size={16} aria-hidden="true" /> Limpar</button>
        </div>
      </div>}
      {edicao && <p className="annotate-hint">{ferramenta === 'SETA' ? 'Arraste na foto: a ponta da seta fica onde você soltar.' : 'Clique na foto onde o texto deve ficar.'}</p>}
      <div className="photo-stage">
        <FotoAnotada src={fotoUrl(laminaId, aberta.id)} alt={aberta.legenda || 'Foto da lâmina'} anotacoes={edicao ? edicao.anotacoes : aberta.anotacoes ?? []} modo="inteira"
          editor={edicao ? { ferramenta, cor, texto: textoMarcacao, aoAdicionar: adicionar } : undefined} />
      </div>
      {edicao
        ? <>
            <label className="annotate-caption">Legenda <span>Opcional</span><input maxLength={200} value={edicao.legenda} onChange={event => setEdicao(atual => atual && { ...atual, legenda: event.target.value })} placeholder="Ex.: A e B mostram a parede celular" /></label>
            {erro && <p className="select-error" role="alert">{erro}</p>}
            <div className="modal-actions"><button className="button secondary" type="button" disabled={salvando} onClick={() => { setEdicao(null); setErro(''); }}>Cancelar</button><button className="button primary" type="button" disabled={salvando} onClick={() => void salvarEdicao()}>{salvando ? 'Salvando…' : 'Salvar marcações'}</button></div>
          </>
        : <>
            <p className="photo-meta">Enviada por {aberta.enviadaPorNome} em {quando(aberta.enviadaEm)}</p>
            {erro && <p className="select-error" role="alert">{erro}</p>}
            {canEdit && <div className="modal-actions"><button className="button danger" type="button" disabled={apagando} onClick={() => void apagar(aberta)}><Trash2 size={15} aria-hidden="true" /> {apagando ? 'Apagando…' : 'Apagar foto'}</button><button className="button primary" type="button" onClick={() => comecarEdicao(aberta)}><PenLine size={15} aria-hidden="true" /> Anotar</button></div>}
          </>}
    </Modal>}
  </section>;
}

/** Comentários da lâmina: lista do mais antigo para o mais novo e caixa para escrever. */
export function SlideComments({ laminaId, usuarioId, canComment, api = colaboracaoApi }: { laminaId: string; usuarioId: number; canComment: boolean; api?: ColaboracaoApi }) {
  const [comentarios, setComentarios] = useState<ComentarioLaminaDTO[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [apagandoId, setApagandoId] = useState<string | null>(null);
  const [erro, setErro] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setStatus('loading');
    api.listarComentarios(laminaId, controller.signal)
      .then(lista => { setComentarios(lista); setStatus('ready'); })
      .catch(error => { if (!controller.signal.aborted && !isUnauthorized(error)) setStatus('error'); });
    return () => controller.abort();
  }, [api, laminaId]);

  async function comentar(event: FormEvent) {
    event.preventDefault();
    if (!texto.trim() || enviando) return;
    setEnviando(true);
    setErro('');
    try {
      const novo = await api.comentar(laminaId, texto);
      setComentarios(atual => [...atual, novo]);
      setTexto('');
    } catch (error) {
      if (!isUnauthorized(error)) setErro(mensagem(error, 'Não foi possível enviar o comentário. Tente de novo.'));
    } finally {
      setEnviando(false);
    }
  }

  async function apagar(comentario: ComentarioLaminaDTO) {
    if (apagandoId) return;
    setApagandoId(comentario.id);
    setErro('');
    try {
      await api.apagarComentario(laminaId, comentario.id);
      setComentarios(atual => atual.filter(item => item.id !== comentario.id));
    } catch (error) {
      if (!isUnauthorized(error)) setErro(mensagem(error, 'Não foi possível apagar o comentário. Tente de novo.'));
    } finally {
      setApagandoId(null);
    }
  }

  return <section className="comments-section" aria-labelledby="comentarios-titulo">
    <div className="collab-heading"><h2 id="comentarios-titulo" className={typeClass('title')}>Comentários{comentarios.length > 0 && <span className="count-badge">{comentarios.length}</span>}</h2></div>
    {status === 'loading' ? <p className="collab-empty">Carregando os comentários…</p>
      : status === 'error' ? <p className="collab-empty" role="alert">Não foi possível carregar os comentários.</p>
      : comentarios.length === 0 ? <p className="collab-empty">Nenhum comentário ainda.</p>
      : <ol className="comment-list" aria-label="Comentários da lâmina">{comentarios.map(comentario => <li key={comentario.id} className="comment">
          <div className="comment-head"><strong>{comentario.autorNome}</strong><time dateTime={comentario.criadoEm}>{quando(comentario.criadoEm)}</time>
            {canComment && comentario.autorUsuarioId === usuarioId && <button className="text-button" type="button" disabled={apagandoId !== null} onClick={() => void apagar(comentario)}>{apagandoId === comentario.id ? 'Apagando…' : <>Apagar<span className="sr-only"> comentário de {quando(comentario.criadoEm)}</span></>}</button>}
          </div>
          <p>{comentario.texto}</p>
        </li>)}</ol>}
    {erro && <p className="select-error" role="alert">{erro}</p>}
    {canComment && <form className="comment-form" onSubmit={event => void comentar(event)}>
      <label className="sr-only" htmlFor="novo-comentario">Escrever comentário</label>
      <textarea id="novo-comentario" maxLength={2000} value={texto} onChange={event => setTexto(event.target.value)} placeholder="Escreva um comentário para quem acompanha esta lâmina…" />
      <div><button className="button primary" disabled={enviando || !texto.trim()}>{enviando ? 'Enviando…' : 'Comentar'}</button></div>
    </form>}
  </section>;
}
