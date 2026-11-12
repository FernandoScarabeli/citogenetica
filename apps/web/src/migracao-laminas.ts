import type { ContagemApi } from './api/contagem-api';
import type { FaseProtocoloDTO, ProtocoloDTO, TipoPersonalizadoDTO, VersaoProtocoloDTO } from './api/contracts/contagem';
import { ApiError, NetworkError, isConflict, isUnauthorized } from './api/errors';
import { mensagemDeErro, normalizarNomeTipo } from './contagem-utils';
import { slideGoal, type Entry, type Slide } from './domain';
import { load, save } from './storage';

/**
 * Envio das lâminas guardadas só no navegador para o servidor.
 *
 * Regras que protegem os dados:
 * - todo identificador é estável (o do próprio registro, se já for UUIDv4, ou derivado de um texto fixo), então
 *   repetir o envio nunca duplica nada;
 * - a lâmina só sai do navegador depois que o servidor confirma o mesmo total de células;
 * - uma lâmina com erro fica no navegador e pode ser reenviada.
 */

type MigracaoApi = Pick<ContagemApi, 'listarProtocolos' | 'listarTiposPersonalizados' | 'criarTipoPersonalizado' | 'criarLamina' | 'criarRegistro' | 'buscarResumo'>;

const UUID_V4 = /^[\da-f]{8}-[\da-f]{4}-4[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/;
const TIPO_NAO_INFORMADO = 'Não informado';

export type ProgressoMigracao = {
  laminaIndice: number;
  laminaTotal: number;
  laminaNome: string;
  celulasEnviadas: number;
  celulasTotal: number;
};
export type FalhaMigracao = { id: string; nome: string; motivo: string };
export type ResultadoMigracao = {
  enviadas: { id: string; nome: string }[];
  falhas: FalhaMigracao[];
  /** Lâminas que nem chegaram a ser tentadas porque o envio foi interrompido. */
  naoTentadas: number;
  /** Motivo da interrupção geral (rede ou sessão), quando houve. */
  interrupcao: string | null;
  /** Lâminas enviadas que não puderam sair do navegador. */
  avisos: string[];
};

/** Falha prevista de uma lâmina, com mensagem pronta para a pessoa. */
class FalhaDeEnvio extends Error {}

/** UUIDv4 canônico (minúsculo), derivado de forma determinística do SHA-256 do texto. */
export async function uuidDeterministico(texto: string): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(texto)));
  const bytes = digest.slice(0, 16);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** Mantém o identificador local se já for UUIDv4 canônico minúsculo; senão deriva um a partir de `texto`. */
export async function idEstavel(idLocal: string, texto: string): Promise<string> {
  return UUID_V4.test(idLocal) ? idLocal : uuidDeterministico(texto);
}

const interrompe = (error: unknown) => error instanceof NetworkError || isUnauthorized(error);

function motivoDe(error: unknown): string {
  if (error instanceof FalhaDeEnvio) return error.message;
  if (error instanceof ApiError && error.status === 401) return 'Sua sessão terminou. Entre de novo para continuar.';
  return mensagemDeErro(error, 'Erro inesperado ao enviar.');
}

function exigirResposta(resposta: unknown, oQue: string): void {
  if (!resposta || typeof resposta !== 'object') throw new FalhaDeEnvio(`O sistema não confirmou ${oQue}.`);
}

/** Laboratório de destino de cada lâmina; `null` envia como avulsa. */
export type DestinoDaLamina = (slide: Slide) => string | null;

/** Limites que a API aceita para a data de criação; fora deles a data original não é enviada. */
const CRIACAO_MAIS_ANTIGA = Date.parse('2020-01-01T00:00:00Z');

/** Data de criação original da lâmina, quando legível e plausível; senão `undefined` (vale o dia do envio). */
function criadaEmOriginal(slide: Slide): string | undefined {
  const data = new Date(slide.createdAt);
  const instante = data.getTime();
  return Number.isNaN(instante) || instante < CRIACAO_MAIS_ANTIGA || instante > Date.now() ? undefined : data.toISOString();
}

type ContextoLamina = {
  api: MigracaoApi;
  usuarioId: number;
  laboratorioId: string | null;
  versao: VersaoProtocoloDTO;
  codigo: string;
  personalizados: Map<string, string>;
};

async function resolverTipo(contexto: ContextoLamina, fase: FaseProtocoloDTO, nomeOriginal: string): Promise<string> {
  const nome = nomeOriginal.trim() || TIPO_NAO_INFORMADO;
  const chave = normalizarNomeTipo(nome);
  const fixo = fase.tipos.find(tipo => normalizarNomeTipo(tipo.nome) === chave);
  if (fixo) return fixo.id;

  const memo = `${fase.id}|${chave}`;
  const conhecido = contexto.personalizados.get(memo);
  if (conhecido) return conhecido;

  const { api, usuarioId, laboratorioId, versao, codigo } = contexto;
  const escopo = laboratorioId ?? 'avulsa';
  const id = await uuidDeterministico(`tipo|${usuarioId}|${escopo}|${codigo}|${fase.nome}|${chave}`);
  let resolvido: string;
  try {
    const criado = await api.criarTipoPersonalizado({ id, faseId: fase.id, laboratorioId, nome });
    exigirResposta(criado, 'o tipo de anormalidade');
    resolvido = id;
  } catch (error) {
    if (!isConflict(error)) throw error;
    const existentes: TipoPersonalizadoDTO[] = await api.listarTiposPersonalizados(versao.id, laboratorioId);
    const igual = existentes.find(tipo => tipo.faseId === fase.id && normalizarNomeTipo(tipo.nome) === chave);
    if (!igual) throw new FalhaDeEnvio(`Não foi possível usar o tipo de anormalidade "${nome}" na fase ${fase.nome}.`);
    resolvido = igual.id;
  }
  contexto.personalizados.set(memo, resolvido);
  return resolvido;
}

async function enviarCelula(contexto: ContextoLamina, slide: Slide, laminaId: string, entry: Entry): Promise<void> {
  const fase = contexto.versao.fases.find(item => item.nome === entry.phase);
  if (!fase) throw new FalhaDeEnvio(`A fase "${entry.phase}" não existe no protocolo atual.`);
  const id = await idEstavel(entry.id, `registro|${contexto.usuarioId}|${slide.id}|${entry.id}`);
  const anormal = entry.status === 'anormal';
  const tipoIds = anormal ? [await resolverTipo(contexto, fase, entry.type)] : undefined;
  const data = new Date(entry.createdAt);
  const registro = await contexto.api.criarRegistro(laminaId, {
    id,
    faseId: fase.id,
    status: anormal ? 'ANORMAL' : 'NORMAL',
    ...(tipoIds ? { tipoIds } : {}),
    quantidade: 1,
    // Sem fusoAparelho: é assim que o histórico reconhece uma contagem antiga e mostra a data original.
    ...(Number.isNaN(data.getTime()) ? {} : { horaAparelho: data.toISOString() }),
  });
  exigirResposta(registro, 'a célula');
}

function protocoloDe(protocolos: ProtocoloDTO[], slide: Slide): ProtocoloDTO {
  const protocolo = protocolos.find(item => item.codigo === slide.protocol);
  if (!protocolo) throw new FalhaDeEnvio('O protocolo da lâmina não foi encontrado no sistema.');
  return protocolo;
}

/** Retira só esta lâmina do navegador, relendo o estado antes (outra aba pode ter mudado). Devolve um aviso se falhar. */
function removerDoNavegador(slide: Slide): string | null {
  const atual = load();
  if (atual.error) return `A lâmina "${slide.name}" foi guardada, mas não saiu deste computador: ${atual.error}`;
  const restantes = atual.state.slides.filter(item => item.id !== slide.id);
  if (restantes.length === atual.state.slides.length) return null;
  if (!save({ ...atual.state, slides: restantes })) {
    return `A lâmina "${slide.name}" foi guardada, mas não saiu deste computador. Guardar de novo é seguro: nada será duplicado.`;
  }
  return null;
}

async function enviarLamina(
  opcoes: { api: MigracaoApi; usuarioId: number; destino: DestinoDaLamina; protocolos: ProtocoloDTO[] },
  slide: Slide,
  avisarCelula: (enviadas: number) => void,
): Promise<void> {
  const { api, usuarioId } = opcoes;
  const laboratorioId = opcoes.destino(slide);
  const criadaEm = criadaEmOriginal(slide);
  const protocolo = protocoloDe(opcoes.protocolos, slide);
  const laminaId = await idEstavel(slide.id, `lamina|${usuarioId}|${slide.id}`);
  const lamina = await api.criarLamina({
    id: laminaId,
    protocoloVersaoId: protocolo.versaoAtual.id,
    ...(laboratorioId ? { laboratorioId } : {}),
    nome: slide.name,
    especie: slide.species,
    tratamento: slide.treatment,
    observacoes: slide.notes,
    meta: slideGoal(slide),
    ...(criadaEm ? { criadaEm } : {}),
  });
  exigirResposta(lamina, 'a lâmina');

  const contexto: ContextoLamina = {
    api, usuarioId, laboratorioId, versao: protocolo.versaoAtual, codigo: protocolo.codigo, personalizados: new Map(),
  };
  let enviadas = 0;
  for (const entry of slide.entries) {
    await enviarCelula(contexto, slide, laminaId, entry);
    enviadas += 1;
    avisarCelula(enviadas);
  }

  const resumo = await api.buscarResumo(laminaId);
  if (!resumo || resumo.total !== slide.entries.length) {
    throw new FalhaDeEnvio(`O sistema confirmou ${resumo?.total ?? 0} de ${slide.entries.length} células. A lâmina continua neste computador.`);
  }
}

export async function migrarLaminas(opcoes: {
  api: MigracaoApi;
  usuarioId: number;
  destino: DestinoDaLamina;
  laminas: Slide[];
  onProgresso?: (progresso: ProgressoMigracao) => void;
}): Promise<ResultadoMigracao> {
  const { api, usuarioId, destino, laminas, onProgresso } = opcoes;
  const resultado: ResultadoMigracao = { enviadas: [], falhas: [], naoTentadas: 0, interrupcao: null, avisos: [] };
  if (laminas.length === 0) return resultado;

  let protocolos: ProtocoloDTO[];
  try {
    protocolos = await api.listarProtocolos();
  } catch (error) {
    resultado.interrupcao = motivoDe(error);
    resultado.naoTentadas = laminas.length;
    return resultado;
  }

  for (const [indice, slide] of laminas.entries()) {
    const progresso = (celulasEnviadas: number): ProgressoMigracao => ({
      laminaIndice: indice + 1, laminaTotal: laminas.length, laminaNome: slide.name, celulasEnviadas, celulasTotal: slide.entries.length,
    });
    onProgresso?.(progresso(0));
    try {
      await enviarLamina({ api, usuarioId, destino, protocolos }, slide, enviadas => onProgresso?.(progresso(enviadas)));
    } catch (error) {
      resultado.falhas.push({ id: slide.id, nome: slide.name, motivo: motivoDe(error) });
      if (interrompe(error)) {
        resultado.interrupcao = motivoDe(error);
        resultado.naoTentadas = laminas.length - indice - 1;
        return resultado;
      }
      continue;
    }
    resultado.enviadas.push({ id: slide.id, nome: slide.name });
    const aviso = removerDoNavegador(slide);
    if (aviso) resultado.avisos.push(aviso);
  }
  return resultado;
}
