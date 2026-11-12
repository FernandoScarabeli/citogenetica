import type { ContagemApi } from './api/contagem-api';
import type { LaboratorioResponseDTO } from './api/contracts/laboratorio';
import type { Slide } from './domain';
import { migrarLaminas } from './migracao-laminas';
import { load } from './storage';

/**
 * Laboratório de destino: o da lâmina, se a pessoa ainda pode criar lâmina nele (ativo e na lista dela);
 * caso contrário a lâmina vai como avulsa.
 */
export function laboratorioDestino(slide: Slide, laboratorios: LaboratorioResponseDTO[]): LaboratorioResponseDTO | null {
  const id = slide.labId?.toLowerCase();
  return (id && laboratorios.find(lab => lab.estado === 'ATIVO' && lab.id.toLowerCase() === id)) || null;
}

/** Trava do módulo: impede duas migrações ao mesmo tempo (remontagem, StrictMode, duas abas de tela). */
let emAndamento = false;

/**
 * Guarda no sistema, uma de cada vez, as lâminas que só existem neste navegador. Não pede nada à pessoa:
 * o que falhar continua no navegador e é tentado de novo na próxima chamada.
 * Devolve quantas lâminas foram guardadas (0 se não havia nada, se já havia uma migração em curso ou se tudo falhou).
 */
export async function migrarLaminasLocais(opcoes: { api: ContagemApi; usuarioId: number; laboratorios: LaboratorioResponseDTO[] }): Promise<number> {
  if (emAndamento) return 0;
  const local = load();
  if (local.error || local.state.slides.length === 0) return 0;
  emAndamento = true;
  try {
    const { api, usuarioId, laboratorios } = opcoes;
    const resultado = await migrarLaminas({
      api, usuarioId, laminas: local.state.slides, destino: slide => laboratorioDestino(slide, laboratorios)?.id ?? null,
    });
    return resultado.enviadas.length;
  } catch {
    return 0;
  } finally {
    emAndamento = false;
  }
}
