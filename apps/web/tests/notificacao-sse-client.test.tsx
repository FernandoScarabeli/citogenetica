import { afterEach, describe, expect, it, vi } from 'vitest';
import { createNotificacaoSseClient, type NotificacaoEventSource } from '../src/api/notificacao-sse-client';

class FakeEventSource implements NotificacaoEventSource {
  readyState = 0;
  onopen: ((event: Event) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  closed = false;
  listeners = new Map<string, (event: Event) => void>();

  addEventListener(type: string, listener: (event: Event) => void) {
    this.listeners.set(type, listener);
  }

  close() {
    this.closed = true;
    this.readyState = 2;
  }

  open() {
    this.readyState = 1;
    this.onopen?.(new Event('open'));
  }

  emitNotification() {
    this.listeners.get('notification')?.(new MessageEvent('notification', { data: '{"type":"INFO"}' }));
  }

  fail(terminal = false) {
    this.readyState = terminal ? 2 : 0;
    this.onerror?.(new Event('error'));
  }
}

afterEach(() => vi.useRealTimers());

describe('cliente SSE de notificações', () => {
  it('abre uma conexão com credenciais e invalida os dados quando chega um evento', () => {
    const source = new FakeEventSource();
    const createEventSource = vi.fn(() => source);
    const changed = vi.fn();
    const client = createNotificacaoSseClient({ createEventSource });
    const stop = client.start(changed, async () => true);

    expect(createEventSource).toHaveBeenCalledWith('/api/events/subscribe', { withCredentials: true });
    source.open();
    source.emitNotification();
    expect(changed).toHaveBeenCalledTimes(2);

    stop();
    expect(source.closed).toBe(true);
  });

  it('reconecta após espera crescente e para ao receber uma sessão inválida', async () => {
    vi.useFakeTimers();
    const sources: FakeEventSource[] = [];
    const createEventSource = vi.fn(() => {
      const source = new FakeEventSource();
      sources.push(source);
      return source;
    });
    let sessionIsValid = true;
    const client = createNotificacaoSseClient({ createEventSource });
    client.start(vi.fn(), async () => sessionIsValid);

    sources[0].fail();
    await vi.advanceTimersByTimeAsync(999);
    expect(createEventSource).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(createEventSource).toHaveBeenCalledTimes(2);

    sources[1].fail();
    await vi.advanceTimersByTimeAsync(1_999);
    expect(createEventSource).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1);
    expect(createEventSource).toHaveBeenCalledTimes(3);

    sessionIsValid = false;
    sources[2].fail(true);
    await vi.waitFor(() => expect(sources[2].closed).toBe(true));
    await vi.advanceTimersByTimeAsync(60_000);
    expect(createEventSource).toHaveBeenCalledTimes(3);
  });

  it('aguarda o Retry-After do backend em vez de repetir uma recusa 429 em ciclo', async () => {
    vi.useFakeTimers();
    const sources: FakeEventSource[] = [];
    const createEventSource = vi.fn(() => {
      const source = new FakeEventSource();
      sources.push(source);
      return source;
    });
    const client = createNotificacaoSseClient({ createEventSource });
    client.start(vi.fn(), async () => true);
    sources[0].fail(true);

    await vi.advanceTimersByTimeAsync(29_999);
    expect(createEventSource).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(createEventSource).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(60_000);
    expect(createEventSource).toHaveBeenCalledTimes(2);
  });
});
