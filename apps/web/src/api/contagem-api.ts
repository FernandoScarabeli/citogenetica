import type { HttpClient } from './http-client';
import { httpClient } from './http-client';
import type {
  AtualizarLaminaRequestDTO,
  AtualizarRegistroRequestDTO,
  CriarLaminaRequestDTO,
  CriarRegistroRequestDTO,
  CriarTipoPersonalizadoRequestDTO,
  DesfazerRegistroRequestDTO,
  DesfazerRegistroResponseDTO,
  LaminaDTO,
  PageDTO,
  ProtocoloDTO,
  RegistroDTO,
  RegistroHistoricoDTO,
  ResumoLaminaDTO,
  TipoPersonalizadoDTO,
  VersaoProtocoloDTO,
} from './contracts/contagem';

const LAMINAS = '/api/v1/laminas';
const PROTOCOLOS = '/api/v1/protocolos';
const TIPOS = '/api/v1/tipos-anormalidade/personalizados';

export interface ListarLaminasOptions {
  laboratorioId?: string | null;
  page?: number;
  size?: number;
  signal?: AbortSignal;
}

export interface ListarRegistrosOptions {
  page?: number;
  size?: number;
  signal?: AbortSignal;
}

function withSignal(signal?: AbortSignal) {
  return signal ? { signal } : {};
}

export function createContagemApi(client: Pick<HttpClient, 'request'> = httpClient) {
  return {
    listarProtocolos(signal?: AbortSignal): Promise<ProtocoloDTO[]> {
      return client.request<ProtocoloDTO[]>(PROTOCOLOS, withSignal(signal));
    },

    buscarVersaoProtocolo(versaoId: string, signal?: AbortSignal): Promise<VersaoProtocoloDTO> {
      return client.request<VersaoProtocoloDTO>(`${PROTOCOLOS}/versoes/${encodeURIComponent(versaoId)}`, withSignal(signal));
    },

    listarTiposPersonalizados(versaoId: string, laboratorioId?: string | null, signal?: AbortSignal): Promise<TipoPersonalizadoDTO[]> {
      const query = new URLSearchParams({ versaoId });
      if (laboratorioId) query.set('laboratorioId', laboratorioId);
      return client.request<TipoPersonalizadoDTO[]>(`${TIPOS}?${query.toString()}`, withSignal(signal));
    },

    criarTipoPersonalizado(request: CriarTipoPersonalizadoRequestDTO, signal?: AbortSignal): Promise<TipoPersonalizadoDTO> {
      return client.request<TipoPersonalizadoDTO>(TIPOS, { method: 'POST', json: request, ...withSignal(signal) });
    },

    listarLaminas(options: ListarLaminasOptions = {}): Promise<PageDTO<LaminaDTO>> {
      const query = new URLSearchParams({
        page: String(options.page ?? 0),
        size: String(options.size ?? 10),
      });
      if (options.laboratorioId) query.set('laboratorioId', options.laboratorioId);
      return client.request<PageDTO<LaminaDTO>>(`${LAMINAS}?${query.toString()}`, withSignal(options.signal));
    },

    buscarLamina(laminaId: string, signal?: AbortSignal): Promise<LaminaDTO> {
      return client.request<LaminaDTO>(`${LAMINAS}/${encodeURIComponent(laminaId)}`, withSignal(signal));
    },

    criarLamina(request: CriarLaminaRequestDTO, signal?: AbortSignal): Promise<LaminaDTO> {
      return client.request<LaminaDTO>(LAMINAS, { method: 'POST', json: request, ...withSignal(signal) });
    },

    atualizarLamina(laminaId: string, request: AtualizarLaminaRequestDTO, signal?: AbortSignal): Promise<LaminaDTO> {
      return client.request<LaminaDTO>(`${LAMINAS}/${encodeURIComponent(laminaId)}`, {
        method: 'PATCH', json: request, ...withSignal(signal),
      });
    },

    excluirLamina(laminaId: string, versaoEsperada: number, signal?: AbortSignal): Promise<void> {
      return client.request<void>(`${LAMINAS}/${encodeURIComponent(laminaId)}?versaoEsperada=${versaoEsperada}`, {
        method: 'DELETE', ...withSignal(signal),
      });
    },

    criarRegistro(laminaId: string, request: CriarRegistroRequestDTO, signal?: AbortSignal): Promise<RegistroDTO> {
      return client.request<RegistroDTO>(`${LAMINAS}/${encodeURIComponent(laminaId)}/registros`, {
        method: 'POST', json: request, ...withSignal(signal),
      });
    },

    listarRegistros(laminaId: string, options: ListarRegistrosOptions = {}): Promise<PageDTO<RegistroHistoricoDTO>> {
      const query = new URLSearchParams({
        page: String(options.page ?? 0),
        size: String(options.size ?? 10),
      });
      return client.request<PageDTO<RegistroHistoricoDTO>>(
        `${LAMINAS}/${encodeURIComponent(laminaId)}/registros?${query.toString()}`, withSignal(options.signal));
    },

    atualizarRegistro(laminaId: string, registroId: string, request: AtualizarRegistroRequestDTO, signal?: AbortSignal): Promise<RegistroDTO> {
      return client.request<RegistroDTO>(
        `${LAMINAS}/${encodeURIComponent(laminaId)}/registros/${encodeURIComponent(registroId)}`,
        { method: 'PATCH', json: request, ...withSignal(signal) });
    },

    desfazerRegistros(laminaId: string, request: DesfazerRegistroRequestDTO, signal?: AbortSignal): Promise<DesfazerRegistroResponseDTO> {
      return client.request<DesfazerRegistroResponseDTO>(`${LAMINAS}/${encodeURIComponent(laminaId)}/registros/desfazer`, {
        method: 'POST', json: request, ...withSignal(signal),
      });
    },

    buscarResumo(laminaId: string, signal?: AbortSignal): Promise<ResumoLaminaDTO> {
      return client.request<ResumoLaminaDTO>(`${LAMINAS}/${encodeURIComponent(laminaId)}/resumo`, withSignal(signal));
    },
  };
}

export type ContagemApi = ReturnType<typeof createContagemApi>;

export const contagemApi = createContagemApi();
