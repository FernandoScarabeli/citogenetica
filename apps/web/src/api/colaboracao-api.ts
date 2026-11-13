import { httpClient, type HttpClient } from './http-client.js';

export interface FotoLaminaDTO {
  id: string;
  legenda: string | null;
  tamanhoBytes: number;
  contentType: string;
  enviadaPorUsuarioId: number;
  enviadaPorNome: string;
  enviadaEm: string;
}

export interface ComentarioLaminaDTO {
  id: string;
  autorUsuarioId: number;
  autorNome: string;
  texto: string;
  criadoEm: string;
}

const base = (laminaId: string) => `/api/v1/laminas/${encodeURIComponent(laminaId)}`;

/** Endereço da imagem; o conteúdo de uma foto nunca muda, então o navegador pode guardar em cache. */
export const fotoUrl = (laminaId: string, fotoId: string) => `${base(laminaId)}/fotos/${encodeURIComponent(fotoId)}/arquivo`;

export function createColaboracaoApi(client: Pick<HttpClient, 'request'> = httpClient) {
  return {
    listarFotos(laminaId: string, signal?: AbortSignal): Promise<FotoLaminaDTO[]> {
      return client.request<FotoLaminaDTO[]>(`${base(laminaId)}/fotos`, signal ? { signal } : {});
    },

    enviarFoto(laminaId: string, arquivo: File, legenda: string): Promise<FotoLaminaDTO> {
      const form = new FormData();
      form.append('arquivo', arquivo);
      if (legenda.trim()) form.append('legenda', legenda.trim());
      return client.request<FotoLaminaDTO>(`${base(laminaId)}/fotos`, { method: 'POST', form });
    },

    apagarFoto(laminaId: string, fotoId: string): Promise<void> {
      return client.request<void>(`${base(laminaId)}/fotos/${encodeURIComponent(fotoId)}`, { method: 'DELETE' });
    },

    listarComentarios(laminaId: string, signal?: AbortSignal): Promise<ComentarioLaminaDTO[]> {
      return client.request<ComentarioLaminaDTO[]>(`${base(laminaId)}/comentarios`, signal ? { signal } : {});
    },

    comentar(laminaId: string, texto: string): Promise<ComentarioLaminaDTO> {
      return client.request<ComentarioLaminaDTO>(`${base(laminaId)}/comentarios`, { method: 'POST', json: { texto } });
    },

    apagarComentario(laminaId: string, comentarioId: string): Promise<void> {
      return client.request<void>(`${base(laminaId)}/comentarios/${encodeURIComponent(comentarioId)}`, { method: 'DELETE' });
    },
  };
}

export type ColaboracaoApi = ReturnType<typeof createColaboracaoApi>;
export const colaboracaoApi = createColaboracaoApi();
