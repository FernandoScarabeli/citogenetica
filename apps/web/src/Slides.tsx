import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { ArrowRight, ChevronRight, FlaskConical, Microscope, Plus, Search, X } from 'lucide-react';
import { typeClass } from '@/lib/type-scale';
import { contagemApi, type ContagemApi } from './api/contagem-api';
import type { LaminaDTO, ProtocoloDTO } from './api/contracts/contagem';
import type { LaboratorioResponseDTO } from './api/contracts/laboratorio';
import { formatarData, mensagemDeErro } from './contagem-utils';
import { LocalSlidesPanel } from './LocalSlidesPanel';
import type { ResultadoMigracao } from './migracao-laminas';
import { SlideDetail } from './SlideDetail';
import { Modal, ProtocolSelect, type ProtocolOption } from './SlideWidgets';

const LIST_PAGE_SIZE = 100;
const DEFAULT_MITOSIS_GOAL = '1000';

const protocolLabel = (protocolo: ProtocoloDTO) => (protocolo.codigo === 'mitose' ? 'Mitose · ciclo celular' : protocolo.nome);

/** Carrega todas as páginas, em sequência, para que a busca na tela valha para a lista inteira. */
async function loadAllSlides(api: ContagemApi, laboratorioId: string | null, signal: AbortSignal): Promise<LaminaDTO[]> {
  const slides: LaminaDTO[] = [];
  let page = 0;
  let totalPages = 1;
  while (page < totalPages) {
    const result = await api.listarLaminas({ ...(laboratorioId ? { laboratorioId } : {}), page, size: LIST_PAGE_SIZE, signal });
    slides.push(...result.content);
    totalPages = result.totalPages;
    page += 1;
  }
  return slides;
}

function MigrationReport({ result, onDismiss }: { result: ResultadoMigracao; onDismiss: () => void }) {
  const sent = result.enviadas.length;
  return <section className="local-slides-report" aria-label="Resultado do envio" role="status">
    <p><strong>{sent === 1 ? '1 lâmina enviada' : `${sent} lâminas enviadas`} para o servidor.</strong>{result.falhas.length > 0 && ` ${result.falhas.length === 1 ? '1 lâmina não foi enviada' : `${result.falhas.length} lâminas não foram enviadas`} e continua neste navegador.`}</p>
    {result.interrupcao && <p>O envio foi interrompido: {result.interrupcao}{result.naoTentadas > 0 && ` ${result.naoTentadas === 1 ? '1 lâmina ainda não foi tentada.' : `${result.naoTentadas} lâminas ainda não foram tentadas.`}`}</p>}
    {result.falhas.length > 0 && <ul>{result.falhas.map(falha => <li key={falha.id}><strong>{falha.nome}</strong>: {falha.motivo}</li>)}</ul>}
    {result.avisos.length > 0 && <ul>{result.avisos.map(aviso => <li key={aviso}>{aviso}</li>)}</ul>}
    <button className="text-button" type="button" onClick={onDismiss}>Dispensar</button>
  </section>;
}

export function Slides({ laboratory, onBack, readOnly, usuarioId, api = contagemApi }: {
  laboratory: LaboratorioResponseDTO | null;
  onBack: () => void;
  readOnly: boolean;
  usuarioId: number;
  api?: ContagemApi;
}) {
  const laboratoryId = laboratory?.id ?? null;
  const canCreate = !readOnly;
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [loadError, setLoadError] = useState('');
  const [retry, setRetry] = useState(0);
  const [slides, setSlides] = useState<LaminaDTO[]>([]);
  const [protocolos, setProtocolos] = useState<ProtocoloDTO[]>([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [migration, setMigration] = useState<ResultadoMigracao | null>(null);
  const [active, setActive] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [creating, setCreating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [createError, setCreateError] = useState('');
  const newSlideId = useRef('');
  const [form, setForm] = useState({ nome: '', protocolo: 'mitose', especie: '', tratamento: '', meta: DEFAULT_MITOSIS_GOAL });

  useEffect(() => {
    const controller = new AbortController();
    setStatus('loading');
    Promise.all([api.listarProtocolos(controller.signal), loadAllSlides(api, laboratoryId, controller.signal)])
      .then(([loadedProtocols, loadedSlides]) => {
        if (controller.signal.aborted) return;
        setProtocolos(loadedProtocols);
        setSlides(loadedSlides);
        setStatus('ready');
      })
      .catch(failure => {
        if (controller.signal.aborted) return;
        setLoadError(mensagemDeErro(failure, 'Não foi possível carregar as lâminas.'));
        setStatus('error');
      });
    return () => controller.abort();
  }, [api, laboratoryId, retry]);

  useEffect(() => { if (readOnly) setCreating(false); }, [readOnly]);

  const upsert = useCallback((slide: LaminaDTO) => {
    setSlides(current => (current.some(item => item.id === slide.id) ? current.map(item => (item.id === slide.id ? slide : item)) : [slide, ...current]));
  }, []);

  const options: ProtocolOption[] = [...protocolos]
    .sort((a, b) => Number(b.codigo === 'mitose') - Number(a.codigo === 'mitose'))
    .map(protocolo => ({ value: protocolo.codigo, label: protocolLabel(protocolo) }));
  const protocolOf = (slide: LaminaDTO) => protocolos.find(protocolo => protocolo.versaoAtual.id === slide.protocoloVersaoId);
  const slide = slides.find(item => item.id === active);

  function openNew() {
    if (!canCreate) return;
    newSlideId.current = crypto.randomUUID();
    setCreateError('');
    setForm({ nome: '', protocolo: protocolos.some(item => item.codigo === 'mitose') ? 'mitose' : (protocolos[0]?.codigo ?? ''), especie: '', tratamento: '', meta: DEFAULT_MITOSIS_GOAL });
    setCreating(true);
  }

  async function create(event: FormEvent) {
    event.preventDefault();
    const protocolo = protocolos.find(item => item.codigo === form.protocolo);
    if (!canCreate || saving || !form.nome.trim() || !protocolo) return;
    setSaving(true);
    setCreateError('');
    try {
      const created = await api.criarLamina({
        id: newSlideId.current,
        protocoloVersaoId: protocolo.versaoAtual.id,
        ...(laboratoryId ? { laboratorioId: laboratoryId } : {}),
        nome: form.nome.trim(),
        especie: form.especie.trim(),
        tratamento: form.tratamento.trim(),
        meta: form.meta ? Number(form.meta) : null,
      });
      upsert(created);
      setCreating(false);
      setActive(created.id);
    } catch (failure) {
      setCreateError(mensagemDeErro(failure, 'Não foi possível criar a lâmina.'));
    } finally {
      setSaving(false);
    }
  }

  function migrationFinished(result: ResultadoMigracao) {
    setMigration(result);
    setRetry(value => value + 1);
  }

  function leaveDetail(message = '') {
    setActive(null);
    setNotice(message);
  }

  const matched = slides.filter(item => `${item.nome} ${item.especie} ${item.tratamento}`.toLocaleLowerCase('pt-BR').includes(query.toLocaleLowerCase('pt-BR')));
  const totalCells = slides.reduce((sum, item) => sum + item.totalCelulas, 0);
  const scopeName = laboratory?.nome ?? 'Lâminas avulsas';

  return <>
    <div className={`container slide-navigation${readOnly ? ' labs-read-only' : ''}`}>
      <nav className="lab-scope-nav" aria-label="Caminho de navegação">
        <button className="text-button" type="button" onClick={onBack}>Meus laboratórios</button><ChevronRight size={14} aria-hidden="true" />
        {slide ? <><span>{scopeName}</span><ChevronRight size={14} aria-hidden="true" /><button className="text-button" type="button" onClick={() => leaveDetail()}>Lâminas</button><ChevronRight size={14} aria-hidden="true" /><strong aria-current="page">{slide.nome}</strong></> : <strong aria-current="page">{scopeName}</strong>}
      </nav>
    </div>
    {readOnly && <p className="container lab-muted">Este laboratório está desativado. Os registros estão disponíveis somente para leitura.</p>}
    {notice && <p className="container slide-notice" role="status">{notice}</p>}
    {error && <div className="error-banner" role="alert">{error}<button type="button" onClick={() => setError('')} aria-label="Dispensar"><X size={16} /></button></div>}
    {status === 'loading' ? <main className="container home slide-state" aria-live="polite"><p>Carregando as lâminas…</p></main>
      : status === 'error' ? <main className="container home slide-state"><h1 className={typeClass('title')}>Não foi possível carregar as lâminas</h1><p>{loadError}</p><button className="button secondary" type="button" onClick={() => setRetry(value => value + 1)}>Tentar de novo</button></main>
      : !slide ? <main className="container home">
        <div className="page-heading"><div><h1 className={typeClass('display')}>Lâminas</h1><p>Organize suas lâminas e acompanhe a contagem de células.</p></div>{canCreate && <button className="button primary" type="button" onClick={openNew}><Plus size={17} /> Nova lâmina</button>}</div>
        {migration && <MigrationReport result={migration} onDismiss={() => setMigration(null)} />}
        {canCreate && <LocalSlidesPanel api={api} usuarioId={usuarioId} laboratorioId={laboratoryId} onFinished={migrationFinished} />}
        <section className="overview" aria-label="Visão geral"><div className="overview-intro"><span className="overview-symbol"><FlaskConical size={22} /></span><div><h2 className={typeClass('title')}>Resumo</h2><p>Totais das lâminas e células registradas.</p></div></div><div className="overview-stat"><span>Lâminas registradas</span><strong>{slides.length}</strong></div><div className="overview-stat"><span>Células contadas</span><strong>{totalCells.toLocaleString('pt-BR')}</strong></div></section>
        <section className="list-section"><div className="section-heading"><div><h2 className={typeClass('title')}>Registros</h2></div><label className="search"><Search size={17} /><span className="sr-only">Buscar lâminas</span><input placeholder="Buscar lâmina" value={query} onChange={event => setQuery(event.target.value)} /></label></div>
          {matched.length ? <div className="slide-list"><div className="list-labels"><span>Identificação</span><span>Protocolo</span><span>Células</span><span>Criada por</span><span>Criada em</span><span /></div>{matched.map(item => <button className="slide-row" type="button" key={item.id} onClick={() => { setNotice(''); setActive(item.id); }}><span className="slide-identity"><span><strong>{item.nome}</strong>{(item.especie || item.tratamento) && <small>{[item.especie, item.tratamento].filter(Boolean).join(' · ')}</small>}</span></span><span><span className="protocol-tag">{protocolOf(item)?.nome ?? 'Protocolo'}</span></span><span className="numeric">{item.totalCelulas.toLocaleString('pt-BR')}</span><span className="slide-creator"><span className="creator-label">Criada por: </span>{item.donoNome}</span><span className="date">{formatarData(item.criadaEm)}</span><ChevronRight size={17} className="row-arrow" /></button>)}</div>
            : <div className="empty-state"><span className="empty-symbol"><Microscope size={25} /></span><h3>{query ? 'Nenhuma lâmina encontrada' : 'Comece pela primeira lâmina'}</h3><p>{query ? 'Tente buscar por outra identificação, espécie ou tratamento.' : canCreate ? 'Crie uma lâmina para registrar suas observações e acompanhar a contagem de células.' : 'Ainda não há lâminas neste laboratório.'}</p>{!query && canCreate && <button className="button secondary" type="button" onClick={openNew}><Plus size={16} /> Nova lâmina</button>}</div>}</section>
      </main>
        : <SlideDetail key={slide.id} api={api} laminaId={slide.id} usuarioId={usuarioId} readOnly={readOnly} protocolos={protocolos} onError={setError} onGone={message => { setSlides(current => current.filter(item => item.id !== slide.id)); leaveDetail(message); }} onLaminaChanged={upsert} onDeleted={id => { setSlides(current => current.filter(item => item.id !== id)); leaveDetail(); }} />}
    <footer className="footer container"><span>Laboratório de Citogenética · UFLA</span></footer>
    {creating && <Modal title="Nova lâmina" onClose={() => setCreating(false)}><form onSubmit={event => void create(event)}>
      <p className="modal-description">Identifique a lâmina e escolha o protocolo da análise.</p>
      <label>Identificação da lâmina<input autoFocus required maxLength={120} placeholder="Ex.: L1 – planta A" value={form.nome} onChange={event => setForm({ ...form, nome: event.target.value })} /></label>
      <ProtocolSelect value={form.protocolo} options={options} onChange={protocolo => setForm({ ...form, protocolo, meta: protocolo === 'mitose' ? DEFAULT_MITOSIS_GOAL : '' })} />
      <label>Meta de contagem <span>Opcional</span><input type="number" min="1" step="1" max="10000000" value={form.meta} onChange={event => setForm({ ...form, meta: event.target.value })} placeholder="Quantidade de células" /></label>
      {form.protocolo === 'mitose' && <div className="form-grid"><label>Espécie <span>Opcional</span><input maxLength={120} value={form.especie} onChange={event => setForm({ ...form, especie: event.target.value })} placeholder="Nome da espécie" /></label><label>Tratamento <span>Opcional</span><input maxLength={120} value={form.tratamento} onChange={event => setForm({ ...form, tratamento: event.target.value })} placeholder="Ex.: controle" /></label></div>}
      {createError && <p className="select-error" role="alert">{createError}</p>}
      <div className="modal-actions"><button type="button" className="button secondary" onClick={() => setCreating(false)}>Cancelar</button><button className="button primary" disabled={saving}>Criar lâmina <ArrowRight size={16} /></button></div>
    </form></Modal>}
  </>;
}
