import { httpClient, type HttpClient } from './http-client.js';

/** Cor em hex (#rrggbb). Marcações antigas podem trazer um nome: azul, amarelo, vermelho, branco ou preto. */
export type CorAnotacao = string;

/** Marcação sobre a foto; coordenadas de 0 a 1 relativas à imagem. Seta vai de (x1, y1) até a ponta (x2, y2). */
export type AnotacaoFotoDTO =
  | { tipo: 'SETA'; x1: number; y1: number; x2: number; y2: number; cor: CorAnotacao; texto?: null }
  | { tipo: 'TEXTO'; x1: number; y1: number; texto: string; cor: CorAnotacao; tamanho?: number | null; x2?: null; y2?: null };

export interface FotoLaminaDTO {
  id: string;
  legenda: string | null;
  tamanhoBytes: number;
  contentType: string;
  enviadaPorUsuarioId: number;
  enviadaPorNome: string;
  enviadaEm: string;
  anotacoes?: AnotacaoFotoDTO[];
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

    editarFoto(laminaId: string, fotoId: string, dados: { legenda: string; anotacoes: AnotacaoFotoDTO[] }): Promise<FotoLaminaDTO> {
      return client.request<FotoLaminaDTO>(`${base(laminaId)}/fotos/${encodeURIComponent(fotoId)}`, {
        method: 'PUT', json: { legenda: dados.legenda.trim() || null, anotacoes: dados.anotacoes },
      });
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
