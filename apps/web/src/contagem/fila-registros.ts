import type { CriarRegistroRequestDTO } from '../api/contracts/contagem';
import { ApiError } from '../api/errors';

/** Chave própria e versionada. Não tem relação com `citogenetica:workspace:v1` (lâminas locais). */
export const CHAVE_FILA_REGISTROS = 'citogenetica:fila-registros:v1';

/** Intervalo entre tentativas de reenvio enquanto houver células guardadas. */
export const INTERVALO_REENVIO_MS = 15_000;

/**
 * Célula contada sem conexão, guardada neste aparelho até ser enviada. O pedido é o mesmo que seria enviado na hora
 * (com o `id` já gerado e a hora/fuso do clique), então repetir o envio é idempotente.
 */
export interface RegistroGuardado {
  usuarioId: number;
  laminaId: string;
  pedido: CriarRegistroRequestDTO;
}

function ehRegistroGuardado(valor: unknown): valor is RegistroGuardado {
  if (typeof valor !== 'object' || valor === null) return false;
  const item = valor as Partial<RegistroGuardado>;
  const pedido = item.pedido as Partial<CriarRegistroRequestDTO> | undefined;
  return typeof item.usuarioId === 'number' && typeof item.laminaId === 'string'
    && typeof pedido === 'object' && pedido !== null
    && typeof pedido.id === 'string' && typeof pedido.faseId === 'string';
}

function lerTudo(): RegistroGuardado[] {
  try {
    const bruto = localStorage.getItem(CHAVE_FILA_REGISTROS);
    const lido: unknown = bruto ? JSON.parse(bruto) : [];
    return Array.isArray(lido) ? lido.filter(ehRegistroGuardado) : [];
  } catch {
    return [];
  }
}

function gravarTudo(itens: RegistroGuardado[]): boolean {
  try {
    if (itens.length) localStorage.setItem(CHAVE_FILA_REGISTROS, JSON.stringify(itens));
    else localStorage.removeItem(CHAVE_FILA_REGISTROS);
    return true;
  } catch {
    return false;
  }
}

/** Células guardadas da pessoa nesta lâmina, na ordem em que foram contadas. */
export function lerFila(usuarioId: number, laminaId: string): RegistroGuardado[] {
  return lerTudo().filter(item => item.usuarioId === usuarioId && item.laminaId === laminaId);
}

/** Total de células guardadas da pessoa, em todas as lâminas. */
export function contarGuardados(usuarioId: number): number {
  return lerTudo().filter(item => item.usuarioId === usuarioId).length;
}

/** Guarda a célula no fim da fila. Devolve false se o aparelho não deixou gravar. */
export function guardarRegistro(item: RegistroGuardado): boolean {
  const itens = lerTudo();
  if (itens.some(atual => atual.pedido.id === item.pedido.id)) return true;
  return gravarTudo([...itens, item]);
}

export function removerRegistro(idRegistro: string): void {
  gravarTudo(lerTudo().filter(item => item.pedido.id !== idRegistro));
}

/** Recusa definitiva: o pedido nunca será aceito, então reenviar não adianta. 401/408/429 são temporários. */
export function ehRecusaDefinitiva(erro: unknown): erro is ApiError {
  return erro instanceof ApiError && erro.status >= 400 && erro.status < 500
    && ![401, 408, 429].includes(erro.status);
}

export const mensagemFilaGuardada = (total: number) =>
  `Sem internet. ${total} ${total === 1 ? 'célula guardada' : 'células guardadas'} neste aparelho — ${total === 1 ? 'será enviada' : 'serão enviadas'} quando a conexão voltar.`;

export const mensagemFilaEnviando = (total: number) =>
  `Enviando ${total} ${total === 1 ? 'célula' : 'células'}…`;

export const mensagemConfirmarSaida = (total: number) =>
  `Há ${total} ${total === 1 ? 'célula ainda não enviada' : 'células ainda não enviadas'} neste aparelho. `
  + `${total === 1 ? 'Ela fica guardada e será enviada' : 'Elas ficam guardadas e serão enviadas'} quando você entrar de novo.`;
