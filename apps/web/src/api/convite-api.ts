import type { HttpClient } from './http-client';
import { httpClient } from './http-client';
import type {
  ConsumirConviteRequestDTO,
  ConviteLaboratorioResponseDTO,
  CriarConviteLaboratorioRequestDTO,
  PreviaConviteResponseDTO,
} from './contracts/convite';
import type { PapelLaboratorio } from './contracts/laboratorio';

const LABORATORIES_PATH = '/api/v1/laboratorios';
const INVITATIONS_PATH = '/api/v1/convites';

export function createConviteApi(client: Pick<HttpClient, 'request'> = httpClient) {
  return {
    criar(laboratorioId: string, email: string, papeis: PapelLaboratorio[], signal?: AbortSignal): Promise<ConviteLaboratorioResponseDTO> {
      const request: CriarConviteLaboratorioRequestDTO = { email, papeis };
      return client.request<ConviteLaboratorioResponseDTO>(`${LABORATORIES_PATH}/${encodeURIComponent(laboratorioId)}/convites`, {
        method: 'POST', json: request, ...(signal ? { signal } : {}),
      });
    },

    listar(laboratorioId: string, signal?: AbortSignal): Promise<ConviteLaboratorioResponseDTO[]> {
      return client.request<ConviteLaboratorioResponseDTO[]>(`${LABORATORIES_PATH}/${encodeURIComponent(laboratorioId)}/convites`, signal ? { signal } : {});
    },

    reenviar(laboratorioId: string, conviteId: string, signal?: AbortSignal): Promise<ConviteLaboratorioResponseDTO> {
      return client.request<ConviteLaboratorioResponseDTO>(`${LABORATORIES_PATH}/${encodeURIComponent(laboratorioId)}/convites/${encodeURIComponent(conviteId)}/reenviar`, {
        method: 'POST', ...(signal ? { signal } : {}),
      });
    },

    revogar(laboratorioId: string, conviteId: string, signal?: AbortSignal): Promise<void> {
      return client.request<void>(`${LABORATORIES_PATH}/${encodeURIComponent(laboratorioId)}/convites/${encodeURIComponent(conviteId)}`, {
        method: 'DELETE', ...(signal ? { signal } : {}),
      });
    },

    previa(token: string, signal?: AbortSignal): Promise<PreviaConviteResponseDTO> {
      const request: ConsumirConviteRequestDTO = { token };
      return client.request<PreviaConviteResponseDTO>(`${INVITATIONS_PATH}/previa`, {
        method: 'POST', json: request, ...(signal ? { signal } : {}),
      });
    },

    aceitar(token: string, signal?: AbortSignal): Promise<void> {
      const request: ConsumirConviteRequestDTO = { token };
      return client.request<void>(`${INVITATIONS_PATH}/aceitar`, {
        method: 'POST', json: request, ...(signal ? { signal } : {}),
      });
    },

    recusar(token: string, signal?: AbortSignal): Promise<void> {
      const request: ConsumirConviteRequestDTO = { token };
      return client.request<void>(`${INVITATIONS_PATH}/recusar`, {
        method: 'POST', json: request, ...(signal ? { signal } : {}),
      });
    },
  };
}

export const conviteApi = createConviteApi();
