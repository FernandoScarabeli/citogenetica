import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ImagePlus, Trash2 } from 'lucide-react';
import { typeClass } from '@/lib/type-scale';
import { colaboracaoApi, fotoUrl, type ColaboracaoApi, type ComentarioLaminaDTO, type FotoLaminaDTO } from './api/colaboracao-api';
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
  const arquivoRef = useRef<HTMLInputElement>(null);

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
          <button type="button" className="photo-thumb" onClick={() => setAberta(foto)} aria-label={`Abrir foto${foto.legenda ? `: ${foto.legenda}` : ''}`}>
            <img src={fotoUrl(laminaId, foto.id)} alt="" loading="lazy" />
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

    {aberta && <Modal title={aberta.legenda || 'Foto da lâmina'} className="photo-dialog" onClose={() => setAberta(null)}>
      <img className="photo-full" src={fotoUrl(laminaId, aberta.id)} alt={aberta.legenda || 'Foto da lâmina'} />
      <p className="photo-meta">Enviada por {aberta.enviadaPorNome} em {quando(aberta.enviadaEm)}</p>
      {canEdit && <div className="modal-actions"><button className="button danger" type="button" disabled={apagando} onClick={() => void apagar(aberta)}><Trash2 size={15} aria-hidden="true" /> {apagando ? 'Apagando…' : 'Apagar foto'}</button></div>}
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
