import type { HttpClient } from './http-client';
import { httpClient } from './http-client';
import type { PreviaConviteResponseDTO } from './contracts/convite';
import type {
  ConsumirConviteOrientacaoRequestDTO,
  ConviteOrientacaoResponseDTO,
  CriarConviteOrientacaoRequestDTO,
  VinculoOrientacaoResponseDTO,
} from './contracts/orientacao';

const LABORATORIES_PATH = '/api/v1/laboratorios';
const ORIENTATION_INVITATIONS_PATH = '/api/v1/orientacoes/convites';

export function createOrientacaoApi(client: Pick<HttpClient, 'request'> = httpClient) {
  return {
    listarVinculos(laboratorioId: string, signal?: AbortSignal): Promise<VinculoOrientacaoResponseDTO[]> {
      return client.request<VinculoOrientacaoResponseDTO[]>(
        `${LABORATORIES_PATH}/${encodeURIComponent(laboratorioId)}/orientacoes`,
        signal ? { signal } : {},
      );
    },

    encerrarVinculo(laboratorioId: string, vinculoId: string, signal?: AbortSignal): Promise<void> {
      return client.request<void>(
        `${LABORATORIES_PATH}/${encodeURIComponent(laboratorioId)}/orientacoes/${encodeURIComponent(vinculoId)}`,
        { method: 'DELETE', ...(signal ? { signal } : {}) },
      );
    },

    criarConvite(laboratorioId: string, email: string, signal?: AbortSignal): Promise<ConviteOrientacaoResponseDTO> {
      const request: CriarConviteOrientacaoRequestDTO = { email };
      return client.request<ConviteOrientacaoResponseDTO>(
        `${LABORATORIES_PATH}/${encodeURIComponent(laboratorioId)}/orientacoes/convites`,
        { method: 'POST', json: request, ...(signal ? { signal } : {}) },
      );
    },

    listarConvites(laboratorioId: string, signal?: AbortSignal): Promise<ConviteOrientacaoResponseDTO[]> {
      return client.request<ConviteOrientacaoResponseDTO[]>(
        `${LABORATORIES_PATH}/${encodeURIComponent(laboratorioId)}/orientacoes/convites`,
        signal ? { signal } : {},
      );
    },

    reenviarConvite(laboratorioId: string, conviteId: string, signal?: AbortSignal): Promise<ConviteOrientacaoResponseDTO> {
      return client.request<ConviteOrientacaoResponseDTO>(
        `${LABORATORIES_PATH}/${encodeURIComponent(laboratorioId)}/orientacoes/convites/${encodeURIComponent(conviteId)}/reenviar`,
        { method: 'POST', ...(signal ? { signal } : {}) },
      );
    },

    cancelarConvite(laboratorioId: string, conviteId: string, signal?: AbortSignal): Promise<void> {
      return client.request<void>(
        `${LABORATORIES_PATH}/${encodeURIComponent(laboratorioId)}/orientacoes/convites/${encodeURIComponent(conviteId)}`,
        { method: 'DELETE', ...(signal ? { signal } : {}) },
      );
    },

    previaConvite(token: string, signal?: AbortSignal): Promise<PreviaConviteResponseDTO> {
      const request: ConsumirConviteOrientacaoRequestDTO = { token };
      return client.request<PreviaConviteResponseDTO>(`${ORIENTATION_INVITATIONS_PATH}/previa`, {
        method: 'POST', json: request, ...(signal ? { signal } : {}),
      });
    },

    aceitarConvite(token: string, signal?: AbortSignal): Promise<void> {
      const request: ConsumirConviteOrientacaoRequestDTO = { token };
      return client.request<void>(`${ORIENTATION_INVITATIONS_PATH}/aceitar`, {
        method: 'POST', json: request, ...(signal ? { signal } : {}),
      });
    },

    recusarConvite(token: string, signal?: AbortSignal): Promise<void> {
      const request: ConsumirConviteOrientacaoRequestDTO = { token };
      return client.request<void>(`${ORIENTATION_INVITATIONS_PATH}/recusar`, {
        method: 'POST', json: request, ...(signal ? { signal } : {}),
      });
    },

    /** Resposta pelo sino: a sessão da conta convidada substitui o link do e-mail. */
    responderConvitePeloSino(conviteId: string, resposta: 'aceitar' | 'recusar', signal?: AbortSignal): Promise<void> {
      return client.request<void>(`${ORIENTATION_INVITATIONS_PATH}/${encodeURIComponent(conviteId)}/${resposta}`, {
        method: 'POST', ...(signal ? { signal } : {}),
      });
    },
  };
}

export const orientacaoApi = createOrientacaoApi();
