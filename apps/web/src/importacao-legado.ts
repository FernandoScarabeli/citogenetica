import type { ContagemApi } from './api/contagem-api';
import type { LaminaDTO, ProtocoloDTO } from './api/contracts/contagem';
import { isConflict, isNotFound } from './api/errors';
import type { Entry } from './domain';
import { META_MITOSE_LEGADA, type CelulaLegada, type LaminaLegada } from './leitura-legado';
import {
  FalhaDeEnvio, criadaEmPlausivel, enviarRegistro, exigirResposta, interrompe, motivoDe, uuidDeterministico,
  type ContextoLamina, type MigracaoApi,
} from './migracao-laminas';

/**
 * Envio das lâminas lidas de um backup dos HTMLs antigos.
 *
 * Os identificadores nascem do conteúdo (usuário, destino, protocolo, nome da lâmina e, nas células, o id e a hora do
 * histórico). O id do histórico sozinho não basta: dois backups podem repetir o mesmo id. Por isso importar de novo o
 * mesmo arquivo não cria nada, e importar um backup mais novo só acrescenta as células que faltam.
 */

export type ImportacaoApi = MigracaoApi & Pick<ContagemApi, 'buscarLamina'>;

export type ProgressoImportacao = { laminaIndice: number; laminaTotal: number; laminaNome: string; celulasEnviadas: number; celulasTotal: number };
export type LaminaImportada = {
  nome: string;
  /** `false` quando a lâmina já estava no sistema (de uma importação anterior, inclusive interrompida). */
  nova: boolean;
  /** Células do arquivo confirmadas pelo sistema, novas ou já existentes. */
  celulasConfirmadas: number;
  /** Células que ainda não estavam na lâmina. */
  celulasAcrescentadas: number;
  /** Células corrigidas no arquivo depois da primeira importação: vale o que já está no sistema. */
  celulasEmConflito: number;
};
export type ResultadoImportacao = {
  importadas: LaminaImportada[];
  falhas: { nome: string; motivo: string }[];
  naoTentadas: number;
  /** Motivo da interrupção geral (rede ou sessão), quando houve. */
  interrupcao: string | null;
};

const MENSAGEM_EXCLUIDA = (nome: string) => `A lâmina "${nome}" foi excluída no sistema; não foi importada de novo.`;

export const idDaLaminaLegada = (usuarioId: number, destino: string | null, lamina: Pick<LaminaLegada, 'protocolo' | 'nome'>) =>
  uuidDeterministico(`legado|${usuarioId}|${destino ?? 'avulsa'}|${lamina.protocolo}|${lamina.nome}`);

export const idDaCelulaLegada = (usuarioId: number, destino: string | null, lamina: Pick<LaminaLegada, 'protocolo' | 'nome'>, celula: Pick<CelulaLegada, 'idHistorico' | 't'>) =>
  uuidDeterministico(`legado|${usuarioId}|${destino ?? 'avulsa'}|${lamina.protocolo}|${lamina.nome}|${celula.idHistorico}|${celula.t}`);

const comoEntrada = (celula: CelulaLegada): Entry => ({ id: celula.idHistorico, phase: celula.fase, status: celula.status, type: celula.tipo, createdAt: new Date(celula.t).toISOString() });

/** Mesmo id, outro conteúdo: a lâmina só serve se for da pessoa, do mesmo protocolo e do mesmo destino. */
function conferirLaminaExistente(existente: LaminaDTO, lamina: LaminaLegada, opcoes: { usuarioId: number; destino: string | null; protocolo: ProtocoloDTO }): void {
  const { usuarioId, destino, protocolo } = opcoes;
  const mesmoDestino = (existente.laboratorioId ?? null)?.toLowerCase() === (destino ?? null)?.toLowerCase();
  if (existente.donoUsuarioId !== usuarioId || existente.protocoloVersaoId !== protocolo.versaoAtual.id || !mesmoDestino) {
    throw new FalhaDeEnvio(`Já existe no sistema uma lâmina com o mesmo identificador de "${lamina.nome}", mas ela não é desta pessoa, deste protocolo ou deste destino. Nada foi alterado.`);
  }
}

async function importarLamina(
  opcoes: { api: ImportacaoApi; usuarioId: number; destino: string | null; protocolos: ProtocoloDTO[] },
  lamina: LaminaLegada,
  avisarCelula: (enviadas: number) => void,
): Promise<LaminaImportada> {
  const { api, usuarioId, destino } = opcoes;
  const protocolo = opcoes.protocolos.find(item => item.codigo === lamina.protocolo);
  if (!protocolo) throw new FalhaDeEnvio('O protocolo da lâmina não foi encontrado no sistema.');

  const laminaId = await idDaLaminaLegada(usuarioId, destino, lamina);
  const criadaEm = criadaEmPlausivel(new Date(lamina.celulas[0].t));
  let existente = false;
  let antes = 0;
  try {
    const criada = await api.criarLamina({
      id: laminaId,
      protocoloVersaoId: protocolo.versaoAtual.id,
      ...(destino ? { laboratorioId: destino } : {}),
      nome: lamina.nome,
      especie: lamina.especie,
      tratamento: lamina.tratamento,
      observacoes: lamina.observacoes,
      meta: lamina.protocolo === 'mitose' ? META_MITOSE_LEGADA : null,
      ...(criadaEm ? { criadaEm } : {}),
    });
    exigirResposta(criada, 'a lâmina');
    antes = criada.totalCelulas ?? 0;
    existente = antes > 0;
  } catch (error) {
    if (!isConflict(error)) throw error;
    // Já existe com outro conteúdo (por exemplo, as observações mudaram): segue acrescentando células, sem alterar a lâmina.
    let atual: LaminaDTO;
    try {
      atual = await api.buscarLamina(laminaId);
    } catch (falha) {
      if (isNotFound(falha)) throw new FalhaDeEnvio(MENSAGEM_EXCLUIDA(lamina.nome));
      throw falha;
    }
    conferirLaminaExistente(atual, lamina, { usuarioId, destino, protocolo });
    antes = atual.totalCelulas;
    existente = true;
  }

  const contexto: ContextoLamina = {
    api, usuarioId, laboratorioId: destino, versao: protocolo.versaoAtual, codigo: protocolo.codigo, personalizados: new Map(),
  };
  let confirmadas = 0;
  let emConflito = 0;
  for (const celula of lamina.celulas) {
    try {
      await enviarRegistro(contexto, laminaId, await idDaCelulaLegada(usuarioId, destino, lamina, celula), comoEntrada(celula));
      confirmadas += 1;
    } catch (error) {
      if (isConflict(error)) emConflito += 1;
      else if (isNotFound(error)) throw new FalhaDeEnvio(MENSAGEM_EXCLUIDA(lamina.nome));
      else throw error;
    }
    avisarCelula(confirmadas + emConflito);
  }

  // O sistema pode ter mais células que o arquivo (a pessoa contou mais depois); só não pode ter menos.
  const resumo = await api.buscarResumo(laminaId);
  if (!resumo || resumo.total < confirmadas) {
    throw new FalhaDeEnvio(`O sistema confirmou ${resumo?.total ?? 0} células, menos que as ${confirmadas} enviadas. Importar de novo é seguro.`);
  }
  return {
    nome: lamina.nome, nova: !existente, celulasConfirmadas: confirmadas,
    celulasAcrescentadas: Math.max(0, Math.min(confirmadas, resumo.total - antes)), celulasEmConflito: emConflito,
  };
}

export async function importarLaminasLegadas(opcoes: {
  api: ImportacaoApi;
  usuarioId: number;
  /** Laboratório onde a pessoa está; `null` importa como lâmina avulsa. */
  destino: string | null;
  laminas: LaminaLegada[];
  onProgresso?: (progresso: ProgressoImportacao) => void;
}): Promise<ResultadoImportacao> {
  const { api, usuarioId, destino, laminas, onProgresso } = opcoes;
  const resultado: ResultadoImportacao = { importadas: [], falhas: [], naoTentadas: 0, interrupcao: null };
  if (laminas.length === 0) return resultado;

  let protocolos: ProtocoloDTO[];
  try {
    protocolos = await api.listarProtocolos();
  } catch (error) {
    resultado.interrupcao = motivoDe(error);
    resultado.naoTentadas = laminas.length;
    return resultado;
  }

  for (const [indice, lamina] of laminas.entries()) {
    const progresso = (celulasEnviadas: number): ProgressoImportacao => ({
      laminaIndice: indice + 1, laminaTotal: laminas.length, laminaNome: lamina.nome, celulasEnviadas, celulasTotal: lamina.celulas.length,
    });
    onProgresso?.(progresso(0));
    try {
      resultado.importadas.push(await importarLamina({ api, usuarioId, destino, protocolos }, lamina, enviadas => onProgresso?.(progresso(enviadas))));
    } catch (error) {
      resultado.falhas.push({ nome: lamina.nome, motivo: motivoDe(error) });
      if (interrompe(error)) {
        resultado.interrupcao = motivoDe(error);
        resultado.naoTentadas = laminas.length - indice - 1;
        return resultado;
      }
    }
  }
  return resultado;
}
