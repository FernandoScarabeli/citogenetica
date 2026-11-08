import type { HttpClient } from './http-client';
import { httpClient } from './http-client';
import type { AdminLaboratorioPageDTO } from './contracts/admin';
import type { EstadoLaboratorio } from './contracts/laboratorio';

const BASE_PATH = '/api/v1/admin/laboratorios';

export interface ListarLaboratoriosAdminOptions {
  nome?: string;
  estado?: EstadoLaboratorio;
  page?: number;
  size?: number;
  signal?: AbortSignal;
}

export function createAdminApi(client: Pick<HttpClient, 'request'> = httpClient) {
  return {
    listarLaboratorios(options: ListarLaboratoriosAdminOptions = {}): Promise<AdminLaboratorioPageDTO> {
      const query = new URLSearchParams({
        page: String(options.page ?? 0),
        size: String(options.size ?? 10),
      });
      const nome = options.nome?.trim();
      if (nome) query.set('nome', nome);
      if (options.estado) query.set('estado', options.estado);
      return client.request<AdminLaboratorioPageDTO>(`${BASE_PATH}?${query.toString()}`, options.signal ? { signal: options.signal } : {});
    },

    desativarLaboratorio(laboratorioId: string, signal?: AbortSignal): Promise<void> {
      return client.request<void>(`${BASE_PATH}/${encodeURIComponent(laboratorioId)}/desativar`, {
        method: 'PATCH',
        ...(signal ? { signal } : {}),
      });
    },

    reativarLaboratorio(laboratorioId: string, signal?: AbortSignal): Promise<void> {
      return client.request<void>(`${BASE_PATH}/${encodeURIComponent(laboratorioId)}/reativar`, {
        method: 'PATCH',
        ...(signal ? { signal } : {}),
      });
    },
  };
}

export const adminApi = createAdminApi();
