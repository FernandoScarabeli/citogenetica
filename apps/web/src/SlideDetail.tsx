import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Braces, Check, FileSpreadsheet, FileText, Pencil, Plus, Trash2, Undo2, X } from 'lucide-react';
import { typeClass } from '@/lib/type-scale';
import type { ContagemApi } from './api/contagem-api';
import type {
  LaminaDTO, PageDTO, ProtocoloDTO, RegistroHistoricoDTO, ResumoLaminaDTO, StatusRegistro,
  TipoPersonalizadoDTO, VersaoProtocoloDTO,
} from './api/contracts/contagem';
import { NetworkError, isConflict, isNotFound } from './api/errors';
import { mensagemFilaEnviando, mensagemFilaGuardada } from './contagem/fila-registros';
import { useFilaRegistros } from './contagem/useFilaRegistros';
import {
  agruparPorDia, descricaoDesfazer, formatarData, formatarHora, fusoDoAparelho, instanteExibido, mensagemDeErro, normalizarNomeTipo, rotuloDesfazer,
} from './contagem-utils';
import { pct } from './domain';
import { EntrySelect, Modal, TypeButtons, TypeChecklist, type TypeChoice } from './SlideWidgets';
import { salvarArquivo } from './salvar-arquivo';
import { useNotesAutosave } from './useNotesAutosave';

const HISTORY_PAGE_SIZE = 20;
const num = (value: number) => value.toLocaleString('pt-BR');
const celulas = (value: number) => (value === 1 ? 'célula' : 'células');
const newId = () => crypto.randomUUID();
const DICA_FILA = 'Disponível depois que as células guardadas neste aparelho forem enviadas.';

type Loaded = { lamina: LaminaDTO; versao: VersaoProtocoloDTO; resumo: ResumoLaminaDTO; tipos: TipoPersonalizadoDTO[] };
type History = { items: RegistroHistoricoDTO[]; page: number; totalPages: number };
type Pending = { key: string; faseId: string; status: StatusRegistro };
type Combo = { key: string; tipoIds: string[]; nome: string; quantidade: number };
type ExportacaoKind = 'resumo' | 'registros' | 'relatorio' | 'json';
const EXPORTACOES: { kind: ExportacaoKind; rotulo: string; Icone: typeof FileText }[] = [
  { kind: 'resumo', rotulo: 'Resumo (CSV)', Icone: FileSpreadsheet },
  { kind: 'registros', rotulo: 'Registros (CSV)', Icone: FileSpreadsheet },
  { kind: 'relatorio', rotulo: 'Relatório (PDF)', Icone: FileText },
  { kind: 'json', rotulo: 'Dados completos (JSON)', Icone: Braces },
];
type ModalKind = 'abnormal' | 'edit-record' | 'edit-slide' | 'delete' | 'undo' | null;

const SEM_TIPO = 'Sem tipo informado';

/** Agrupa os registros anormais de uma fase por combinação de tipos, somando as quantidades que restam. */
function combosDaFase(registros: RegistroHistoricoDTO[], faseId: string): Combo[] {
  const mapa = new Map<string, Combo>();
  for (const registro of registros) {
    if (registro.faseId !== faseId || registro.status !== 'ANORMAL' || registro.quantidade < 1) continue;
    const tipoIds = [...registro.tipoIds].sort();
    const key = tipoIds.join('|');
    const nome = registro.tipos.length ? [...registro.tipos].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(tipo => tipo.nome).join(' + ') : SEM_TIPO;
    const atual = mapa.get(key);
    if (atual) atual.quantidade += registro.quantidade;
    else mapa.set(key, { key, tipoIds, nome, quantidade: registro.quantidade });
  }
  return [...mapa.values()].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
}

function historyFrom(page: PageDTO<RegistroHistoricoDTO>): History {
  return { items: page.content, page: page.currentPage, totalPages: page.totalPages };
}

/** Soma ao resumo do servidor as células já contadas na tela e ainda a caminho do servidor. */
function withPending(resumo: ResumoLaminaDTO, pending: Pending[]): ResumoLaminaDTO {
  if (!pending.length) return resumo;
  const fases = resumo.fases.map(fase => {
    const normal = pending.filter(item => item.faseId === fase.faseId && item.status === 'NORMAL').length;
    const abnormal = pending.filter(item => item.faseId === fase.faseId && item.status === 'ANORMAL').length;
    return { ...fase, normais: fase.normais + normal, anormais: fase.anormais + abnormal, total: fase.total + normal + abnormal };
  });
  return { ...resumo, fases, total: resumo.total + pending.length, anormais: resumo.anormais + pending.filter(item => item.status === 'ANORMAL').length };
}

export function SlideDetail({ api, laminaId, usuarioId, readOnly, protocolos, onError, onGone, onLaminaChanged, onDeleted }: {
  api: ContagemApi;
  laminaId: string;
  usuarioId: number;
  readOnly: boolean;
  protocolos: ProtocoloDTO[];
  onError: (message: string) => void;
  onGone: (message: string) => void;
  onLaminaChanged: (lamina: LaminaDTO) => void;
  onDeleted: (laminaId: string) => void;
}) {
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [loadError, setLoadError] = useState('');
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [history, setHistory] = useState<History>({ items: [], page: 0, totalPages: 0 });
  const [loadingMore, setLoadingMore] = useState(false);
  const [pending, setPending] = useState<Pending[]>([]);
  const [tab, setTab] = useState<'contagem' | 'resumo' | 'historico'>('contagem');
  const [modal, setModal] = useState<ModalKind>(null);
  const [busy, setBusy] = useState(false);
  const [baixando, setBaixando] = useState<ExportacaoKind | null>(null);
  const [retry, setRetry] = useState(0);
  const notes = useNotesAutosave(saveNotesText);

  // Formulários dos modais.
  const [phaseId, setPhaseId] = useState('');
  const [recordStatus, setRecordStatus] = useState<StatusRegistro>('ANORMAL');
  const [selectedTypes, setSelectedTypes] = useState<string[]>([]);
  const [otherChecked, setOtherChecked] = useState(false);
  const [otherText, setOtherText] = useState('');
  const [typeError, setTypeError] = useState('');
  const [quantity, setQuantity] = useState('');
  const [editing, setEditing] = useState<RegistroHistoricoDTO | null>(null);
  const [slideForm, setSlideForm] = useState({ nome: '', especie: '', tratamento: '', meta: '' });
  const typeRef = useRef<HTMLInputElement>(null);

  // Ajuste em lote.
  const batchTriggers = useRef<Record<string, HTMLButtonElement | null>>({});
  const [batchPhase, setBatchPhase] = useState<string | null>(null);
  const [batchAction, setBatchAction] = useState<'add' | 'remove'>('add');
  const [batchStatus, setBatchStatus] = useState<StatusRegistro>('NORMAL');
  const [batchQuantity, setBatchQuantity] = useState('');
  const [batchError, setBatchError] = useState('');
  const [comboKey, setComboKey] = useState<string | null>(null);
  const [comboState, setComboState] = useState<{ status: 'loading' | 'ready' | 'error'; registros: RegistroHistoricoDTO[] }>({ status: 'loading', registros: [] });
  const [multi, setMulti] = useState(false);

  const mounted = useRef(true);
  const loadSeq = useRef(0);
  const refreshSeq = useRef(0);
  const tail = useRef<Promise<void>>(Promise.resolve());
  const inFlight = useRef(0);
  const settled = useRef(new Set<string>());
  const callbacks = useRef({ onError, onGone, onLaminaChanged });
  useEffect(() => { callbacks.current = { onError, onGone, onLaminaChanged }; });
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);

  const lamina = loaded?.lamina;
  // O envio das observações pode rodar depois de a tela fechar; por isso lê a lâmina mais recente daqui.
  const laminaRef = useRef(lamina);
  laminaRef.current = lamina;
  const canEdit = !!lamina && !readOnly && lamina.donoUsuarioId === usuarioId;
  const protocolo = lamina ? protocolos.find(item => item.versaoAtual.id === lamina.protocoloVersaoId) : undefined;
  const isMitosis = protocolo?.codigo === 'mitose';

  // Células contadas sem conexão e guardadas neste aparelho: continuam contando na tela até serem enviadas.
  const fila = useFilaRegistros({
    api, usuarioId, laminaId, ativo: status === 'ready',
    onRecusado: (item, erro) => callbacks.current.onError(
      `A célula ${item.pedido.status === 'NORMAL' ? 'normal' : 'anormal'} de ${phaseNameRef.current(item.pedido.faseId)} não entrou na contagem: ${mensagemDeErro(erro, 'ela foi recusada.')}`),
    onSessaoExpirada: () => callbacks.current.onError('Sua sessão expirou. Entre de novo: as células guardadas neste aparelho serão enviadas depois.'),
    onConcluido: () => void refreshRef.current(),
  });
  const guardadas = fila.itens.length;
  const filaRef = useRef(fila);
  filaRef.current = fila;
  const shown = useMemo(() => {
    if (!loaded) return null;
    const naFila = fila.itens.map(({ pedido }) => ({ key: pedido.id, faseId: pedido.faseId, status: pedido.status }));
    return withPending(loaded.resumo, [...pending, ...naFila]);
  }, [loaded, pending, fila.itens]);

  // Cargas -----------------------------------------------------------------------------------------------------

  const load = useCallback(async (signal: AbortSignal, seq: number) => {
    setStatus('loading');
    try {
      const found = await api.buscarLamina(laminaId, signal);
      const owner = !readOnly && found.donoUsuarioId === usuarioId;
      const [versao, resumo, page, tipos] = await Promise.all([
        api.buscarVersaoProtocolo(found.protocoloVersaoId, signal),
        api.buscarResumo(laminaId, signal),
        api.listarRegistros(laminaId, { page: 0, size: HISTORY_PAGE_SIZE, signal }),
        owner ? api.listarTiposPersonalizados(found.protocoloVersaoId, found.laboratorioId, signal) : Promise.resolve([] as TipoPersonalizadoDTO[]),
      ]);
      if (signal.aborted || seq !== loadSeq.current) return;
      setLoaded({ lamina: found, versao, resumo, tipos });
      setHistory(historyFrom(page));
      notes.reset(found.observacoes);
      setStatus('ready');
    } catch (error) {
      if (signal.aborted || seq !== loadSeq.current) return;
      if (isNotFound(error)) { callbacks.current.onGone('Esta lâmina não está mais disponível.'); return; }
      setLoadError(mensagemDeErro(error, 'Não foi possível carregar a lâmina.'));
      setStatus('error');
    }
  }, [api, laminaId, readOnly, usuarioId, notes.reset]);

  useEffect(() => {
    const controller = new AbortController();
    const seq = ++loadSeq.current;
    void load(controller.signal, seq);
    return () => controller.abort();
  }, [load, retry]);

  // Para remover anormais, lista as combinações que existem na fase a partir de todos os registros da lâmina.
  const removendoAnormais = status === 'ready' && batchPhase !== null && batchAction === 'remove' && batchStatus === 'ANORMAL';
  useEffect(() => {
    if (!removendoAnormais) return;
    const controller = new AbortController();
    setComboState({ status: 'loading', registros: [] });
    void (async () => {
      try {
        const registros: RegistroHistoricoDTO[] = [];
        for (let pagina = 0, total = 1; pagina < total; pagina += 1) {
          const result = await api.listarRegistros(laminaId, { page: pagina, size: 100, signal: controller.signal });
          registros.push(...result.content);
          total = result.totalPages;
        }
        if (!controller.signal.aborted) setComboState({ status: 'ready', registros });
      } catch {
        if (!controller.signal.aborted) setComboState({ status: 'error', registros: [] });
      }
    })();
    return () => controller.abort();
  }, [removendoAnormais, batchPhase, api, laminaId]);

  const refreshRef = useRef<() => Promise<void>>(() => Promise.resolve());
  const phaseNameRef = useRef<(faseId: string) => string>(() => 'uma fase');
  phaseNameRef.current = faseId => loaded?.versao.fases.find(fase => fase.id === faseId)?.nome ?? 'uma fase';

  /** Busca de novo lâmina, resumo e primeira página do histórico, e só então descarta as células otimistas. */
  const refresh = useCallback(async () => {
    const seq = ++refreshSeq.current;
    const covered = [...settled.current];
    try {
      const [lamina, resumo, page] = await Promise.all([
        api.buscarLamina(laminaId),
        api.buscarResumo(laminaId),
        api.listarRegistros(laminaId, { page: 0, size: HISTORY_PAGE_SIZE }),
      ]);
      if (!mounted.current) { callbacks.current.onLaminaChanged(lamina); return; }
      if (seq !== refreshSeq.current) return;
      setLoaded(current => (current ? { ...current, lamina, resumo } : current));
      setHistory(historyFrom(page));
      covered.forEach(key => settled.current.delete(key));
      setPending(items => items.filter(item => !covered.includes(item.key)));
      callbacks.current.onLaminaChanged(lamina);
    } catch (error) {
      if (!mounted.current) return;
      if (isNotFound(error)) { callbacks.current.onGone('Esta lâmina não está mais disponível.'); return; }
      callbacks.current.onError(mensagemDeErro(error, 'Não foi possível atualizar a contagem. Recarregue a página.'));
    }
  }, [api, laminaId]);
  refreshRef.current = refresh;

  async function loadMore() {
    if (loadingMore || history.page + 1 >= history.totalPages) return;
    setLoadingMore(true);
    try {
      const page = await api.listarRegistros(laminaId, { page: history.page + 1, size: HISTORY_PAGE_SIZE });
      setHistory(current => {
        const known = new Set(current.items.map(item => item.id));
        return { items: [...current.items, ...page.content.filter(item => !known.has(item.id))], page: page.currentPage, totalPages: page.totalPages };
      });
    } catch (error) {
      onError(mensagemDeErro(error, 'Não foi possível carregar mais registros.'));
    } finally {
      setLoadingMore(false);
    }
  }

  // Contagem rápida --------------------------------------------------------------------------------------------

  // Células já contadas na tela e ainda a caminho do servidor se perderiam ao fechar a página.
  useEffect(() => {
    if (!pending.length) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [pending.length]);

  /** Conta uma célula na hora e a envia em fila; ao esvaziar a fila, recarrega resumo e histórico. */
  function count(faseId: string, recordStatus: StatusRegistro, tipoIds: string[] = []) {
    const key = newId();
    const body = {
      id: key, faseId, status: recordStatus, ...(tipoIds.length ? { tipoIds } : {}), quantidade: 1,
      horaAparelho: new Date().toISOString(), fusoAparelho: fusoDoAparelho(),
    };
    setPending(items => [...items, { key, faseId, status: recordStatus }]);
    inFlight.current += 1;
    tail.current = tail.current.then(async () => {
      try {
        await api.criarRegistro(laminaId, body);
        settled.current.add(key);
      } catch (error) {
        // Sem conexão a célula não some: fica guardada neste aparelho e continua contando.
        const guardada = error instanceof NetworkError && filaRef.current.enfileirar(body);
        if (mounted.current) setPending(items => items.filter(item => item.key !== key));
        if (!guardada) callbacks.current.onError(mensagemDeErro(error, 'Esta célula não foi registrada. Tente de novo.'));
      } finally {
        inFlight.current -= 1;
        if (inFlight.current === 0) void refresh();
      }
    });
  }

  // Tipos de anormalidade ---------------------------------------------------------------------------------------

  function optionsFor(faseId: string, extra: TypeChoice[] = []): TypeChoice[] {
    if (!loaded) return [];
    const fixed = loaded.versao.fases.find(fase => fase.id === faseId)?.tipos ?? [];
    const custom = loaded.tipos.filter(tipo => tipo.faseId === faseId);
    const all = [...fixed, ...custom, ...extra];
    return all.filter((tipo, index) => all.findIndex(other => other.id === tipo.id) === index);
  }

  /** Devolve o id do tipo descrito em "Outro", criando-o só se ainda não existir na fase. */
  async function typeIdFor(faseId: string, text: string): Promise<string> {
    if (!loaded) throw new Error('Lâmina não carregada.');
    const nome = text.trim().replace(/\s+/g, ' ');
    const key = normalizarNomeTipo(nome);
    const known = optionsFor(faseId).find(tipo => normalizarNomeTipo(tipo.nome) === key);
    if (known) return known.id;
    try {
      const created = await api.criarTipoPersonalizado({ id: newId(), faseId, laboratorioId: loaded.lamina.laboratorioId, nome });
      setLoaded(current => (current ? { ...current, tipos: [...current.tipos, created] } : current));
      return created.id;
    } catch (error) {
      if (!isConflict(error)) throw error;
      const list = await api.listarTiposPersonalizados(loaded.lamina.protocoloVersaoId, loaded.lamina.laboratorioId);
      setLoaded(current => (current ? { ...current, tipos: list } : current));
      const fixed = loaded.versao.fases.find(fase => fase.id === faseId)?.tipos ?? [];
      const existing = [...fixed, ...list.filter(tipo => tipo.faseId === faseId)].find(tipo => normalizarNomeTipo(tipo.nome) === key);
      if (existing) return existing.id;
      throw error;
    }
  }

  /** Junta os tipos marcados com o descrito em "Outro". Devolve null se nada foi escolhido. */
  async function chosenTypeIds(faseId: string): Promise<string[] | null> {
    const ids = [...selectedTypes];
    if (otherChecked && otherText.trim()) {
      const id = await typeIdFor(faseId, otherText);
      if (!ids.includes(id)) ids.push(id);
    }
    return ids.length ? ids : null;
  }

  function hasTypeChoice() {
    return selectedTypes.length > 0 || (otherChecked && otherText.trim().length > 0);
  }

  function resetTypes() {
    setSelectedTypes([]); setOtherChecked(false); setOtherText(''); setTypeError(''); setComboKey(null);
  }

  function toggleType(id: string) {
    setTypeError('');
    setSelectedTypes(current => (current.includes(id) ? current.filter(item => item !== id) : [...current, id]));
  }

  // Anormalidade (uma célula) ------------------------------------------------------------------------------------

  function openAbnormal(faseId: string) {
    resetTypes();
    setMulti(false);
    setPhaseId(faseId);
    setModal('abnormal');
  }

  /** Um toque: registra a célula com este tipo pelo mesmo caminho da contagem (inclui a fila sem conexão). */
  function pickAbnormal(tipoId: string) {
    if (!canEdit) return;
    count(phaseId, 'ANORMAL', [tipoId]);
    setModal(null);
  }

  async function submitAbnormal(event: FormEvent) {
    event.preventDefault();
    if (!canEdit || busy) return;
    if (!hasTypeChoice()) { setTypeError('Selecione ao menos uma anormalidade.'); typeRef.current?.focus(); return; }
    setBusy(true);
    try {
      const ids = await chosenTypeIds(phaseId);
      if (!ids) return;
      count(phaseId, 'ANORMAL', ids);
      setModal(null);
    } catch (error) {
      setTypeError(mensagemDeErro(error, 'Não foi possível criar a anormalidade.'));
    } finally {
      setBusy(false);
    }
  }

  // Ajuste em lote -------------------------------------------------------------------------------------------------

  function closeBatch() {
    const closing = batchPhase;
    setBatchPhase(null);
    if (closing) requestAnimationFrame(() => batchTriggers.current[closing]?.focus());
  }

  function openBatch(faseId: string) {
    if (!canEdit) return;
    resetTypes();
    setBatchPhase(faseId);
    setPhaseId(faseId);
    setBatchAction('add');
    setBatchStatus('NORMAL');
    setBatchQuantity('');
    setBatchError('');
  }

  async function submitBatch(event: FormEvent) {
    event.preventDefault();
    if (!canEdit || !batchPhase || busy || guardadas > 0) return;
    const amount = Number(batchQuantity);
    if (!Number.isSafeInteger(amount) || amount < 1) { setBatchError('Informe uma quantidade inteira maior que zero.'); return; }
    const removeCombo = batchAction === 'remove' && batchStatus === 'ANORMAL';
    const combo = removeCombo ? combosDaFase(comboState.registros, batchPhase).find(item => item.key === comboKey) : undefined;
    if (removeCombo) {
      if (!combo) { setBatchError('Escolha qual anormalidade remover.'); return; }
      if (amount > combo.quantidade) { setBatchError(`Só há ${num(combo.quantidade)} ${celulas(combo.quantidade)} com essa anormalidade.`); return; }
    } else if (batchStatus === 'ANORMAL' && !hasTypeChoice()) { setBatchError('Selecione ao menos uma anormalidade.'); return; }
    setBusy(true);
    try {
      let tipoIds: string[] = [];
      if (combo) tipoIds = combo.tipoIds;
      else if (batchStatus === 'ANORMAL') {
        const ids = await chosenTypeIds(batchPhase);
        if (!ids) return;
        tipoIds = ids;
      }
      if (batchAction === 'add') {
        await api.criarRegistro(laminaId, {
          id: newId(), faseId: batchPhase, status: batchStatus, ...(tipoIds.length ? { tipoIds } : {}), quantidade: amount,
          horaAparelho: new Date().toISOString(), fusoAparelho: fusoDoAparelho(),
        });
      } else {
        await api.desfazerRegistros(laminaId, {
          id: newId(), faseId: batchPhase, status: batchStatus, ...(tipoIds.length ? { tipoIds } : {}), quantidade: amount,
        });
      }
      closeBatch();
      await refresh();
    } catch (error) {
      setBatchError(mensagemDeErro(error, 'Não foi possível concluir o ajuste.'));
      if (isConflict(error)) await refresh();
    } finally {
      setBusy(false);
    }
  }

  // Desfazer o último registro ---------------------------------------------------------------------------------------

  const last = history.items[0];

  async function undoLast() {
    if (!canEdit || !last || busy || guardadas > 0) return;
    setModal(null);
    setBusy(true);
    try {
      await api.desfazerRegistros(laminaId, {
        id: newId(), faseId: last.faseId, status: last.status, ...(last.tipoIds.length ? { tipoIds: last.tipoIds } : {}), quantidade: last.quantidade,
      });
    } catch (error) {
      onError(mensagemDeErro(error, 'Não foi possível desfazer o último registro.'));
    } finally {
      await refresh();
      if (mounted.current) setBusy(false);
    }
  }

  // Editar registro ---------------------------------------------------------------------------------------------------

  function openEditRecord(record: RegistroHistoricoDTO) {
    resetTypes();
    setEditing(record);
    setPhaseId(record.faseId);
    setRecordStatus(record.status);
    setSelectedTypes(record.tipoIds);
    setQuantity(String(record.quantidade));
    setModal('edit-record');
  }

  async function submitEditRecord(event: FormEvent) {
    event.preventDefault();
    if (!canEdit || !editing || busy) return;
    const amount = Number(quantity);
    if (!Number.isSafeInteger(amount) || amount < 1) { setTypeError('Informe uma quantidade inteira maior que zero.'); return; }
    if (recordStatus === 'ANORMAL' && !hasTypeChoice()) { setTypeError('Selecione ao menos uma anormalidade.'); typeRef.current?.focus(); return; }
    setBusy(true);
    try {
      const ids = recordStatus === 'ANORMAL' ? await chosenTypeIds(phaseId) : [];
      await api.atualizarRegistro(laminaId, editing.id, {
        versaoEsperada: editing.versao, faseId: phaseId, status: recordStatus, ...(ids?.length ? { tipoIds: ids } : {}), quantidade: amount,
      });
      setModal(null);
      await refresh();
    } catch (error) {
      if (isConflict(error)) {
        setModal(null);
        onError('Este registro foi alterado em outro lugar. Os dados foram atualizados; confira e tente de novo.');
        await refresh();
      } else {
        setTypeError(mensagemDeErro(error, 'Não foi possível salvar o registro.'));
      }
    } finally {
      setBusy(false);
    }
  }

  // Editar e excluir a lâmina ----------------------------------------------------------------------------------------

  const staleSlideMessage = 'Esta lâmina foi alterada em outro lugar. Os dados foram atualizados; confira e tente de novo.';

  async function saveSlide(patch: { nome: string; especie: string; tratamento: string; observacoes: string; meta: number | null }): Promise<boolean> {
    const current = laminaRef.current;
    if (!current) return false;
    try {
      const updated = await api.atualizarLamina(current.id, { versaoEsperada: current.versao, ...patch });
      laminaRef.current = updated;
      setLoaded(state => (state ? { ...state, lamina: updated } : state));
      callbacks.current.onLaminaChanged(updated);
      return true;
    } catch (error) {
      if (isNotFound(error)) callbacks.current.onGone('Esta lâmina não está mais disponível.');
      else if (isConflict(error)) { callbacks.current.onError(staleSlideMessage); await refresh(); }
      else callbacks.current.onError(mensagemDeErro(error, 'Não foi possível salvar a lâmina.'));
      return false;
    }
  }

  function openEditSlide() {
    if (!lamina) return;
    setSlideForm({ nome: lamina.nome, especie: lamina.especie, tratamento: lamina.tratamento, meta: lamina.meta?.toString() ?? '' });
    setModal('edit-slide');
  }

  async function submitSlide(event: FormEvent) {
    event.preventDefault();
    if (!canEdit || !lamina || busy || !slideForm.nome.trim()) return;
    setBusy(true);
    const saved = await saveSlide({
      nome: slideForm.nome.trim(), especie: slideForm.especie.trim(), tratamento: slideForm.tratamento.trim(),
      observacoes: lamina.observacoes, meta: slideForm.meta ? Number(slideForm.meta) : null,
    });
    if (mounted.current) { setBusy(false); if (saved) setModal(null); }
  }

  function saveNotesText(text: string): Promise<boolean> {
    const current = laminaRef.current;
    if (!current) return Promise.resolve(false);
    return saveSlide({ nome: current.nome, especie: current.especie, tratamento: current.tratamento, observacoes: text, meta: current.meta });
  }

  async function removeSlide() {
    if (!canEdit || !lamina || busy || guardadas > 0) return;
    setBusy(true);
    try {
      await api.excluirLamina(lamina.id, lamina.versao);
      onDeleted(lamina.id);
    } catch (error) {
      if (isNotFound(error)) { onGone('Esta lâmina não está mais disponível.'); return; }
      setModal(null);
      if (isConflict(error)) { onError(staleSlideMessage); await refresh(); } else onError(mensagemDeErro(error, 'Não foi possível excluir a lâmina.'));
    } finally {
      if (mounted.current) setBusy(false);
    }
  }

  async function exportar(kind: ExportacaoKind) {
    if (baixando) return;
    setBaixando(kind);
    try {
      const arquivo = await (kind === 'resumo' ? api.baixarLaminaResumoCsv(laminaId)
        : kind === 'registros' ? api.baixarLaminaRegistrosCsv(laminaId)
        : kind === 'relatorio' ? api.baixarLaminaRelatorioPdf(laminaId)
        : api.baixarLaminaJson(laminaId));
      if (mounted.current) salvarArquivo(arquivo);
    } catch (error) {
      if (!mounted.current) return;
      if (isNotFound(error)) onGone('Esta lâmina não está mais disponível.');
      else onError(mensagemDeErro(error, 'Não foi possível baixar o arquivo. Tente de novo.'));
    } finally {
      if (mounted.current) setBaixando(null);
    }
  }

  // Tela --------------------------------------------------------------------------------------------------------------

  if (status === 'loading') return <main className="page-container detail slide-state" aria-live="polite"><p>Carregando a lâmina…</p></main>;
  if (status === 'error' || !loaded || !lamina || !shown) {
    return <main className="page-container detail slide-state"><h1 className={typeClass('title')}>Não foi possível abrir a lâmina</h1><p>{loadError}</p><button className="button secondary" type="button" onClick={() => setRetry(value => value + 1)}>Tentar de novo</button></main>;
  }

  const normal = shown.total - shown.anormais;
  const goal = lamina.meta;
  const phaseName = (faseId: string) => loaded.versao.fases.find(fase => fase.id === faseId)?.nome ?? 'Fase';
  const editPhaseOptions = loaded.versao.fases.map(fase => ({ value: fase.id, label: fase.nome }));
  const extraForEdit = editing && editing.faseId === phaseId ? editing.tipos : [];
  const batchOptions = (faseId: string) => optionsFor(faseId);

  return <main className="page-container detail">
    <div className="detail-heading">
      <div>
        <div className="title-line"><h1 className={typeClass('display')}>{lamina.nome}</h1><span className="protocol-tag">{protocolo?.nome ?? 'Protocolo'}</span></div>
        <p>{(lamina.especie || lamina.tratamento) && <>{[lamina.especie, lamina.tratamento].filter(Boolean).join(' · ')} <span className="dot-separator">·</span> </>}Criada em {formatarData(lamina.criadaEm)}</p>
      </div>
      {canEdit && <div className="detail-actions">
        <button className="button secondary" type="button" disabled={guardadas > 0} title={guardadas > 0 ? DICA_FILA : undefined} onClick={openEditSlide}><Pencil size={15} /> Editar lâmina</button>
        <button className="button icon-only secondary" type="button" title={guardadas > 0 ? DICA_FILA : 'Excluir lâmina'} disabled={guardadas > 0} aria-label="Excluir lâmina" onClick={() => setModal('delete')}><Trash2 size={17} /></button>
      </div>}
    </div>
    {!readOnly && lamina.donoUsuarioId !== usuarioId && <p className="owner-note">Esta lâmina é de {lamina.donoNome}. Você pode consultar a contagem, mas só quem a criou pode alterá-la.</p>}
    <div className="detail-summary">
      <div className="summary-main"><span>Total de células</span><strong>{num(shown.total)}</strong><small>registradas nesta lâmina</small></div>
      <div className="summary-item"><span>Células normais</span><strong>{num(normal)}</strong><small>{pct(normal, shown.total)} do total</small></div>
      <div className="summary-item"><span>Células anormais</span><strong>{num(shown.anormais)}</strong><small>{pct(shown.anormais, shown.total)} do total</small></div>
      {isMitosis && <div className="summary-item"><span>Índice mitótico</span><strong>{pct(shown.indiceMitotico, 100)}</strong><small>células em divisão</small></div>}
    </div>
    {goal !== null && <div className="progress-line"><div><span>Meta de contagem</span><strong>{num(shown.total)} <span>/ {num(goal)} células</span></strong></div><div className="progress-track" role="progressbar" aria-label="Meta de contagem" aria-valuemin={0} aria-valuemax={goal} aria-valuenow={Math.min(shown.total, goal)}><span style={{ width: `${Math.min(shown.total / goal * 100, 100)}%` }} /></div></div>}
    <div className="tabs" role="tablist" aria-label="Seções da lâmina">{([['contagem', 'Contagem'], ['resumo', 'Resumo'], ['historico', 'Histórico']] as const).map(([value, label]) => <button type="button" role="tab" aria-selected={tab === value} className={tab === value ? 'selected' : ''} onClick={() => setTab(value)} key={value}>{label}</button>)}</div>

    {tab === 'contagem' && <div className="workspace"><section className="count-area">
      <div className="section-heading compact"><div><h2 className={typeClass('title')}>Contagem por fase</h2><p>Registre cada célula na fase correspondente.</p></div>
        {canEdit && <div className="count-tools"><button className="text-button" type="button" disabled={!last || pending.length > 0 || guardadas > 0 || busy} title={guardadas > 0 ? DICA_FILA : undefined} onClick={() => (last && last.quantidade > 1 ? setModal('undo') : void undoLast())}><Undo2 size={15} /> {last ? rotuloDesfazer(last, phaseName(last.faseId)) : 'Desfazer último'}</button></div>}
      </div>
      <div className="phase-list">{shown.fases.map(fase => { const combos = removendoAnormais && batchPhase === fase.faseId ? combosDaFase(comboState.registros, fase.faseId) : []; const comboAtual = combos.find(item => item.key === comboKey); return <div className="phase-row" key={fase.faseId}>
        <div className="phase-title"><strong>{fase.nome}</strong><small>{num(fase.total)} {fase.total === 1 ? 'célula' : 'células'} no total</small>
          {canEdit && <button className="phase-adjust" type="button" ref={el => { batchTriggers.current[fase.faseId] = el; }} aria-expanded={batchPhase === fase.faseId} disabled={guardadas > 0} title={guardadas > 0 ? DICA_FILA : undefined} onClick={() => (batchPhase === fase.faseId ? closeBatch() : openBatch(fase.faseId))}>Ajustar quantidade<span className="sr-only"> em {fase.nome}</span></button>}
        </div>
        {canEdit && <div className="phase-actions">
          <button className="count-button" type="button" aria-label={`Registrar ${fase.nome} normal; ${fase.normais} ${fase.normais === 1 ? 'célula normal' : 'células normais'}`} onClick={() => count(fase.faseId, 'NORMAL')}><span className="count-number">{num(fase.normais)}</span><span className="count-label"><Plus size={16} aria-hidden="true" /> Normal</span></button>
          <button className="count-button abnormal-button" type="button" aria-label={`Registrar ${fase.nome} anormal; ${fase.anormais} ${fase.anormais === 1 ? 'célula anormal' : 'células anormais'}`} onClick={() => openAbnormal(fase.faseId)}><span className="count-number">{num(fase.anormais)}</span><span className="count-label"><Plus size={16} aria-hidden="true" /> Anormal</span></button>
        </div>}
        {!canEdit && <div className="phase-actions" aria-label={`${fase.nome}: ${fase.normais} normais, ${fase.anormais} anormais`}><span className="numeric">{num(fase.normais)} normais · {num(fase.anormais)} anormais</span></div>}
        {canEdit && batchPhase === fase.faseId && <form className="batch-editor" onSubmit={event => void submitBatch(event)}>
          <div className="batch-header"><div><h3>Ajustar {fase.nome}</h3><p>Adicione ou remova células desta fase.</p></div><button type="button" className="icon-button" aria-label="Fechar ajuste" onClick={closeBatch}><X size={18} /></button></div>
          <div className="batch-fields">
            <fieldset><legend>O que deseja fazer?</legend><div className="batch-choice"><button type="button" aria-pressed={batchAction === 'add'} onClick={() => { setBatchError(''); setBatchAction('add'); resetTypes(); }}>Adicionar</button><button type="button" aria-pressed={batchAction === 'remove'} onClick={() => { setBatchError(''); setBatchAction('remove'); resetTypes(); }}>Remover</button></div></fieldset>
            <fieldset><legend>Classificação</legend><div className="batch-choice"><button type="button" aria-pressed={batchStatus === 'NORMAL'} onClick={() => { setBatchError(''); setBatchStatus('NORMAL'); resetTypes(); }}>Normal</button><button type="button" aria-pressed={batchStatus === 'ANORMAL'} onClick={() => { setBatchError(''); setBatchStatus('ANORMAL'); resetTypes(); }}>Anormal</button></div></fieldset>
            <label className="batch-quantity">Quantidade de células<input type="number" min="1" step="1" inputMode="numeric" required value={batchQuantity} onChange={event => { setBatchError(''); setBatchQuantity(event.target.value); }} placeholder="Ex.: 10" /></label>
          </div>
          {removendoAnormais && <fieldset className="combo-list"><legend>Qual anormalidade remover?</legend>
            {comboState.status === 'loading' && <p className="batch-hint" role="status">Carregando as anormalidades desta fase…</p>}
            {comboState.status === 'error' && <p className="select-error" role="alert">Não foi possível carregar as anormalidades. Feche e tente de novo.</p>}
            {comboState.status === 'ready' && (combos.length === 0
              ? <p className="batch-hint">Não há células anormais para remover nesta fase.</p>
              : <div className="combo-options">{combos.map(combo => <label className="combo-option" key={combo.key}><input type="radio" name={`combo-${fase.faseId}`} checked={comboKey === combo.key} onChange={() => { setBatchError(''); setComboKey(combo.key); }} /><span>{combo.nome} ({num(combo.quantidade)})</span></label>)}</div>)}
          </fieldset>}
          {batchStatus === 'ANORMAL' && !removendoAnormais && <TypeChecklist legend={batchAction === 'add' ? 'Anormalidades observadas' : 'Anormalidades do registro a remover'} options={batchOptions(fase.faseId)} selected={selectedTypes} onToggle={id => { setBatchError(''); toggleType(id); }} other={otherChecked} onOther={value => { setBatchError(''); setOtherChecked(value); }} allowOther={batchAction === 'add'} otherText={otherText} onOtherText={value => { setBatchError(''); setOtherText(value); }} />}
          {batchError && <p className="select-error" role="alert">{batchError}</p>}
          <div className="batch-footer">{batchAction === 'remove' && batchStatus === 'NORMAL' ? <p>{`Disponível: ${num(fase.normais)} ${celulas(fase.normais)}.`}</p> : comboAtual ? <p>{`Disponível: ${num(comboAtual.quantidade)} ${celulas(comboAtual.quantidade)}.`}</p> : <span />}<div><button type="button" className="button secondary" onClick={closeBatch}>Cancelar</button><button disabled={busy} className={`button ${batchAction === 'remove' ? 'danger' : 'primary'}`}>{batchAction === 'remove' ? 'Remover células' : 'Adicionar células'}</button></div></div>
        </form>}
      </div>; })}</div>
    </section>
    <section className="side-panel notes-panel" aria-label="Observações da lâmina"><div className="notes"><label htmlFor="notes">Observações</label><p>Anotações desta lâmina</p>
      <textarea id="notes" readOnly={!canEdit} placeholder={canEdit ? 'Adicione suas observações...' : ''} value={notes.text} maxLength={5000} onChange={event => notes.setText(event.target.value)} onBlur={() => void notes.flush()} />
      {canEdit && <p className="notes-status" role="status">{notes.state === 'saving' ? 'Salvando…' : notes.state === 'saved' ? 'Salvo' : notes.state === 'error' ? <>Não foi possível salvar. <button className="text-button" type="button" onClick={() => void notes.flush()}>Tentar de novo</button></> : ''}</p>}
    </div></section></div>}

    {tab === 'resumo' && <section className="tab-content"><div className="section-heading"><div><h2 className={typeClass('title')}>Resumo da análise</h2><p>Distribuição das células e anormalidades por fase.</p></div><div className="action-group" role="group" aria-label="Exportar lâmina">{EXPORTACOES.map(({ kind, rotulo, Icone }) => <button key={kind} className="button secondary" type="button" disabled={baixando !== null} aria-busy={baixando === kind} onClick={() => void exportar(kind)}><Icone size={16} aria-hidden="true" /> {baixando === kind ? 'Baixando…' : rotulo}</button>)}</div></div>
      <div className="table-wrap"><table><thead><tr><th>Fase</th><th>Normais</th><th>Anormais</th><th>Total</th><th>% anormais</th></tr></thead><tbody>{shown.fases.map(fase => <tr key={fase.faseId}><td><strong>{fase.nome}</strong>{fase.tipos.map(tipo => <small key={tipo.tipoId}>{tipo.nome}: {tipo.contagem} ({pct(tipo.percentual, 100)})</small>)}</td><td>{fase.normais}</td><td>{fase.anormais}</td><td>{fase.total}</td><td>{pct(fase.percentualAnormais, 100)}</td></tr>)}</tbody></table></div>
      {lamina.observacoes && <div className="report-notes typeset"><h3>Observações</h3><p>{lamina.observacoes}</p></div>}</section>}

    {tab === 'historico' && <section className="tab-content"><div className="section-heading"><div><h2 className={typeClass('title')}>Histórico de registros</h2><p>Do mais recente ao mais antigo.</p></div></div>
      {history.items.length === 0 ? <div className="empty-state small-empty"><p>Nenhuma célula registrada nesta lâmina.</p></div> : <div className="history-list">
        {agruparPorDia(history.items).map(group => <div key={group.dia}><h3 className="history-date">{group.dia}</h3>{group.registros.map(record => <div className="history-row" key={record.id}>
          <div><strong>{phaseName(record.faseId)} · {record.status === 'NORMAL' ? 'Normal' : 'Anormal'}</strong><p>{num(record.quantidade)} {celulas(record.quantidade)}{record.tipos.length > 0 && <> · {record.tipos.map(tipo => tipo.nome).join(', ')}</>}</p></div>
          <time dateTime={instanteExibido(record)}>{formatarHora(instanteExibido(record))}</time>
          {canEdit && <button className="text-button history-edit" type="button" disabled={guardadas > 0} title={guardadas > 0 ? DICA_FILA : undefined} onClick={() => openEditRecord(record)}><Pencil size={14} aria-hidden="true" /> Editar<span className="sr-only"> registro de {phaseName(record.faseId)} às {formatarHora(instanteExibido(record))}</span></button>}
        </div>)}</div>)}
      </div>}
      {history.page + 1 < history.totalPages && <div className="history-more"><button className="button secondary" type="button" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? 'Carregando…' : 'Carregar mais'}</button></div>}
    </section>}

    {guardadas > 0 && <div className="fila-aviso" role="status"><span aria-hidden="true" className="fila-aviso-ponto" />{fila.enviando ? mensagemFilaEnviando(guardadas) : mensagemFilaGuardada(guardadas)}</div>}

    {modal === 'abnormal' && <Modal title="Registrar célula anormal" onClose={() => setModal(null)}>{multi
      ? <form className="entry-form" onSubmit={event => void submitAbnormal(event)}>
        <p className="modal-description">Marque todas as anormalidades observadas na célula.</p>
        <TypeChecklist legend={`Anormalidades em ${phaseName(phaseId)}`} options={optionsFor(phaseId)} selected={selectedTypes} onToggle={toggleType} other={otherChecked} onOther={value => { setTypeError(''); setOtherChecked(value); }} otherText={otherText} onOtherText={value => { setTypeError(''); setOtherText(value); }} error={typeError} firstRef={typeRef} />
        <button type="button" className="text-button multi-toggle" onClick={() => { resetTypes(); setMulti(false); }}>Voltar ao toque único</button>
        <div className="modal-actions"><button type="button" className="button secondary" onClick={() => setModal(null)}>Cancelar</button><button className="button primary" disabled={busy}><Check size={16} /> Registrar célula</button></div>
      </form>
      : <form className="entry-form" onSubmit={event => void submitAbnormal(event)}>
        <p className="modal-description">Toque na anormalidade observada para registrar a célula.</p>
        <TypeButtons legend={`Anormalidades em ${phaseName(phaseId)}`} options={optionsFor(phaseId)} onPick={pickAbnormal} other={otherChecked} onOther={value => { setTypeError(''); setOtherChecked(value); }} otherText={otherText} onOtherText={value => { setTypeError(''); setOtherText(value); }} error={typeError} />
        <button type="button" className="text-button multi-toggle" onClick={() => { resetTypes(); setMulti(true); }}>Marcar várias</button>
        <div className="modal-actions"><button type="button" className="button secondary" onClick={() => setModal(null)}>Cancelar</button>{otherChecked && <button className="button primary" disabled={busy}><Check size={16} /> Registrar célula</button>}</div>
      </form>}</Modal>}

    {modal === 'edit-record' && editing && <Modal title="Editar registro" onClose={() => setModal(null)}><form className="entry-form" onSubmit={event => void submitEditRecord(event)}>
      <p className="modal-description">Corrija a classificação deste lançamento. A data original será mantida.</p>
      <EntrySelect label="Fase" value={phaseId} options={editPhaseOptions} onChange={value => { setPhaseId(value); resetTypes(); }} />
      <EntrySelect label="Classificação" value={recordStatus} options={[{ value: 'NORMAL', label: 'Normal' }, { value: 'ANORMAL', label: 'Anormal' }]} onChange={value => { setRecordStatus(value as StatusRegistro); setTypeError(''); }} />
      <label>Quantidade de células<input type="number" min="1" step="1" inputMode="numeric" required value={quantity} onChange={event => { setTypeError(''); setQuantity(event.target.value); }} /></label>
      {recordStatus === 'ANORMAL' && <TypeChecklist legend="Anormalidades" options={optionsFor(phaseId, extraForEdit)} selected={selectedTypes} onToggle={toggleType} other={otherChecked} onOther={value => { setTypeError(''); setOtherChecked(value); }} otherText={otherText} onOtherText={value => { setTypeError(''); setOtherText(value); }} error={typeError} firstRef={typeRef} />}
      {recordStatus === 'NORMAL' && typeError && <p className="select-error" role="alert">{typeError}</p>}
      <div className="modal-actions"><button type="button" className="button secondary" onClick={() => setModal(null)}>Cancelar</button><button className="button primary" disabled={busy}><Check size={16} /> Salvar alteração</button></div>
    </form></Modal>}

    {modal === 'edit-slide' && <Modal title="Editar lâmina" onClose={() => setModal(null)}><form onSubmit={event => void submitSlide(event)}>
      <label>Identificação da lâmina<input autoFocus required maxLength={120} value={slideForm.nome} onChange={event => setSlideForm({ ...slideForm, nome: event.target.value })} /></label>
      {isMitosis && <div className="form-grid"><label>Espécie<input maxLength={120} value={slideForm.especie} onChange={event => setSlideForm({ ...slideForm, especie: event.target.value })} /></label><label>Tratamento<input maxLength={120} value={slideForm.tratamento} onChange={event => setSlideForm({ ...slideForm, tratamento: event.target.value })} /></label></div>}
      <label>Meta de contagem <span>Opcional</span><input type="number" min="1" step="1" max="10000000" value={slideForm.meta} onChange={event => setSlideForm({ ...slideForm, meta: event.target.value })} placeholder="Quantidade de células" /></label>
      <div className="modal-actions"><button type="button" className="button secondary" onClick={() => setModal(null)}>Cancelar</button><button className="button primary" disabled={busy}>Salvar alterações</button></div>
    </form></Modal>}

    {modal === 'undo' && last && <Modal title="Desfazer este registro?" onClose={() => setModal(null)}>
      <p className="modal-description">Serão removidas {descricaoDesfazer(last, phaseName(last.faseId))}.</p>
      <div className="modal-actions"><button type="button" className="button secondary" onClick={() => setModal(null)}>Cancelar</button><button type="button" className="button danger" disabled={busy} onClick={() => void undoLast()}><Undo2 size={16} /> Desfazer</button></div>
    </Modal>}

    {modal === 'delete' && <Modal title="Excluir lâmina?" onClose={() => setModal(null)}>
      <p className="modal-description">A lâmina <strong>{lamina.nome}</strong> e as {num(lamina.totalCelulas)} {celulas(lamina.totalCelulas)} registradas nela serão excluídas. Esta ação não pode ser desfeita.</p>
      <div className="modal-actions"><button type="button" className="button secondary" onClick={() => setModal(null)}>Cancelar</button><button type="button" className="button danger" disabled={busy} onClick={() => void removeSlide()}><Trash2 size={16} /> Excluir lâmina</button></div>
    </Modal>}
  </main>;
}
