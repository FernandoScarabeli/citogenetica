import type { HttpClient } from './http-client';
import { httpClient } from './http-client';
import type {
  AlterarPapeisParticipacaoRequestDTO,
  LaboratorioResponseDTO,
  ParticipacaoResponseDTO,
  PapelLaboratorio,
  SalvarLaboratorioRequestDTO,
} from './contracts/laboratorio';

const BASE_PATH = '/api/v1/laboratorios';

export function createLaboratorioApi(client: Pick<HttpClient, 'request'> = httpClient) {
  return {
    listarMeus(signal?: AbortSignal): Promise<LaboratorioResponseDTO[]> {
      return client.request<LaboratorioResponseDTO[]>(BASE_PATH, signal ? { signal } : {});
    },

    buscar(laboratorioId: string, signal?: AbortSignal): Promise<LaboratorioResponseDTO> {
      return client.request<LaboratorioResponseDTO>(`${BASE_PATH}/${laboratorioId}`, signal ? { signal } : {});
    },

    criar(nome: string, signal?: AbortSignal): Promise<LaboratorioResponseDTO> {
      const request: SalvarLaboratorioRequestDTO = { nome };
      return client.request<LaboratorioResponseDTO>(BASE_PATH, {
        method: 'POST', json: request, ...(signal ? { signal } : {}),
      });
    },

    atualizarNome(laboratorioId: string, nome: string, signal?: AbortSignal): Promise<LaboratorioResponseDTO> {
      const request: SalvarLaboratorioRequestDTO = { nome };
      return client.request<LaboratorioResponseDTO>(`${BASE_PATH}/${laboratorioId}`, {
        method: 'PATCH',
        json: request,
        ...(signal ? { signal } : {}),
      });
    },

    listarParticipacoes(laboratorioId: string, signal?: AbortSignal): Promise<ParticipacaoResponseDTO[]> {
      return client.request<ParticipacaoResponseDTO[]>(`${BASE_PATH}/${laboratorioId}/participacoes`, signal ? { signal } : {});
    },

    alterarPapeis(
      laboratorioId: string,
      participacaoId: string,
      versaoEsperada: number,
      papeis: PapelLaboratorio[],
      signal?: AbortSignal,
    ): Promise<ParticipacaoResponseDTO> {
      const request: AlterarPapeisParticipacaoRequestDTO = { versaoEsperada, papeis };
      return client.request<ParticipacaoResponseDTO>(
        `${BASE_PATH}/${laboratorioId}/participacoes/${participacaoId}/papeis`,
        { method: 'PATCH', json: request, ...(signal ? { signal } : {}) },
      );
    },

    removerParticipacao(laboratorioId: string, participacaoId: string, signal?: AbortSignal): Promise<void> {
      return client.request<void>(`${BASE_PATH}/${laboratorioId}/participacoes/${participacaoId}`, {
        method: 'DELETE',
        ...(signal ? { signal } : {}),
      });
    },

    sair(laboratorioId: string, signal?: AbortSignal): Promise<void> {
      return client.request<void>(`${BASE_PATH}/${laboratorioId}/participacoes/me`, {
        method: 'DELETE',
        ...(signal ? { signal } : {}),
      });
    },
  };
}

export const laboratorioApi = createLaboratorioApi();
