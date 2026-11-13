import { describe, expect, it, vi } from 'vitest';
import { createNotificacaoApi } from '../src/api/notificacao-api';
import type { HttpClient } from '../src/api/http-client';
import type { NotificacaoSseClient } from '../src/api/notificacao-sse-client';

describe('notificacaoApi', () => {
  it('consulta a contagem e a página com os filtros do contrato', async () => {
    const page = { content: [], number: 2, totalPages: 4, last: false };
    const request = vi.fn().mockResolvedValueOnce({ count: 7 }).mockResolvedValueOnce(page);
    const api = createNotificacaoApi({ request } as unknown as Pick<HttpClient, 'request'>);

    await expect(api.contarNaoLidas()).resolves.toEqual({ count: 7 });
    await expect(api.listar({ page: 2, size: 10, lida: false })).resolves.toEqual(page);

    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/notificacoes/nao-lidas/count', {});
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/notificacoes?page=2&size=10&lida=false', {});
  });

  it('usa os endpoints PATCH para marcar uma ou todas como lidas', async () => {
    const request = vi.fn().mockResolvedValueOnce({ id: 12, lida: true }).mockResolvedValueOnce({ marcadas: 3 });
    const api = createNotificacaoApi({ request } as unknown as Pick<HttpClient, 'request'>);

    await api.marcarLida(12);
    await api.marcarTodasLidas();

    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/notificacoes/12/lida', { method: 'PATCH' });
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/notificacoes/marcar-todas-lidas', { method: 'PATCH' });
  });

  it('mantém uma conexão por usuário, propaga invalidações e a fecha ao encerrar a sessão', async () => {
    const request = vi.fn().mockResolvedValue({ count: 0 });
    let publish: (() => void) | undefined;
    const stopStream = vi.fn();
    const start = vi.fn((onChange: () => void) => {
      publish = onChange;
      return stopStream;
    });
    const sseClient = { start } as unknown as NotificacaoSseClient;
    const api = createNotificacaoApi({ request } as unknown as Pick<HttpClient, 'request'>, sseClient);
    const onChange = vi.fn();
    const unsubscribe = api.subscribeChanges(onChange);

    const releaseFirst = api.startRealtime(15);
    const releaseSecond = api.startRealtime(15);
    expect(start).toHaveBeenCalledTimes(1);
    expect(start).toHaveBeenCalledWith(expect.any(Function), expect.any(Function));

    publish?.();
    expect(onChange).toHaveBeenCalledTimes(1);
    releaseFirst();
    expect(stopStream).not.toHaveBeenCalled();
    releaseSecond();
    expect(stopStream).toHaveBeenCalledTimes(1);

    const releaseThird = api.startRealtime(15);
    api.stopRealtime();
    releaseThird();
    unsubscribe();
    expect(start).toHaveBeenCalledTimes(2);
    expect(stopStream).toHaveBeenCalledTimes(2);
  });

  it('fecha a conexão em 401 e troca de usuário sem conservar a sessão anterior', () => {
    const request = vi.fn();
    let onUnauthorized: (() => void) | undefined;
    const stopStream = vi.fn();
    const sseClient = { start: vi.fn(() => stopStream) } as unknown as NotificacaoSseClient;
    const client = {
      request,
      onUnauthorized(listener: () => void) {
        onUnauthorized = listener;
        return () => { onUnauthorized = undefined; };
      },
    };
    const api = createNotificacaoApi(client, sseClient);

    const releaseFirstUser = api.startRealtime(15);
    onUnauthorized?.();
    expect(stopStream).toHaveBeenCalledTimes(1);
    releaseFirstUser();

    const releaseSecondUser = api.startRealtime(16);
    expect(stopStream).toHaveBeenCalledTimes(1);
    expect(sseClient.start).toHaveBeenCalledTimes(2);
    releaseSecondUser();
    expect(stopStream).toHaveBeenCalledTimes(2);
  });
});
