import { isUnauthorized } from './errors';
import { httpClient, type HttpClient } from './http-client';
import type {
  MarcarTodasLidasResponseDTO,
  NotificacaoCountDTO,
  NotificacaoPageDTO,
  NotificacaoResponseDTO,
} from './contracts/notificacao';
import { createNotificacaoRealtimeClient, type NotificacaoSseClient } from './notificacao-sse-client';

const NOTIFICATIONS_PATH = '/api/v1/notificacoes';

export interface ListarNotificacoesOptions {
  page?: number;
  size?: number;
  lida?: boolean;
  signal?: AbortSignal;
}

export type NotificacaoApiClient = Pick<HttpClient, 'request'> & Partial<Pick<HttpClient, 'onUnauthorized'>>;

export function createNotificacaoApi(
  client: NotificacaoApiClient = httpClient,
  sseClient: NotificacaoSseClient = createNotificacaoRealtimeClient(),
) {
  const changeListeners = new Set<() => void>();
  let realtimeUserId: number | null = null;
  let realtimeGeneration = 0;
  let realtimeReferences = 0;
  let stopStream: (() => void) | undefined;
  let removeUnauthorizedListener: (() => void) | undefined;

  function publishChange(): void {
    for (const listener of changeListeners) listener();
  }

  function stopRealtime(): void {
    realtimeGeneration += 1;
    realtimeUserId = null;
    realtimeReferences = 0;
    removeUnauthorizedListener?.();
    removeUnauthorizedListener = undefined;
    stopStream?.();
    stopStream = undefined;
  }

  async function verifySession(): Promise<boolean> {
    try {
      await client.request<NotificacaoCountDTO>(`${NOTIFICATIONS_PATH}/nao-lidas/count`);
      return true;
    } catch (error) {
      return !isUnauthorized(error);
    }
  }

  function retainRealtime(generation: number): () => void {
    realtimeReferences += 1;
    let released = false;
    return () => {
      if (released || generation !== realtimeGeneration) return;
      released = true;
      realtimeReferences = Math.max(0, realtimeReferences - 1);
      if (realtimeReferences === 0) stopRealtime();
    };
  }

  return {
    listar(options: ListarNotificacoesOptions = {}): Promise<NotificacaoPageDTO> {
      const query = new URLSearchParams({
        page: String(options.page ?? 0),
        size: String(options.size ?? 20),
      });
      if (options.lida !== undefined) query.set('lida', String(options.lida));
      return client.request<NotificacaoPageDTO>(`${NOTIFICATIONS_PATH}?${query.toString()}`, options.signal ? { signal: options.signal } : {});
    },

    contarNaoLidas(signal?: AbortSignal): Promise<NotificacaoCountDTO> {
      return client.request<NotificacaoCountDTO>(`${NOTIFICATIONS_PATH}/nao-lidas/count`, signal ? { signal } : {});
    },

    marcarLida(id: number, signal?: AbortSignal): Promise<NotificacaoResponseDTO> {
      return client.request<NotificacaoResponseDTO>(`${NOTIFICATIONS_PATH}/${encodeURIComponent(String(id))}/lida`, {
        method: 'PATCH',
        ...(signal ? { signal } : {}),
      });
    },

    marcarTodasLidas(signal?: AbortSignal): Promise<MarcarTodasLidasResponseDTO> {
      return client.request<MarcarTodasLidasResponseDTO>(`${NOTIFICATIONS_PATH}/marcar-todas-lidas`, {
        method: 'PATCH',
        ...(signal ? { signal } : {}),
      });
    },

    subscribeChanges(listener: () => void): () => void {
      changeListeners.add(listener);
      return () => changeListeners.delete(listener);
    },

    startRealtime(userId: number): () => void {
      if (realtimeUserId !== userId) {
        stopRealtime();
        realtimeUserId = userId;
        const generation = realtimeGeneration;
        stopStream = sseClient.start(publishChange, verifySession);
        removeUnauthorizedListener = client.onUnauthorized?.(() => stopRealtime());
        realtimeReferences = 0;
        return retainRealtime(generation);
      }

      return retainRealtime(realtimeGeneration);
    },

    stopRealtime,
  };
}

export const notificacaoApi = createNotificacaoApi();
