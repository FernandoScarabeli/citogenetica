import { afterEach, describe, expect, it, vi } from 'vitest';
import { createNotificacaoPollingClient } from '../src/api/notificacao-sse-client';

class FakeDocument {
  hidden = false;
  listeners = new Set<() => void>();
  addEventListener(_type: string, listener: () => void) {
    this.listeners.add(listener);
  }
  removeEventListener(_type: string, listener: () => void) {
    this.listeners.delete(listener);
  }
  setHidden(hidden: boolean) {
    this.hidden = hidden;
    for (const listener of this.listeners) listener();
  }
}

afterEach(() => vi.useRealTimers());

describe('cliente de notificações por consulta periódica', () => {
  it('avisa ao iniciar e a cada intervalo enquanto a sessão é válida', async () => {
    vi.useFakeTimers();
    const doc = new FakeDocument();
    const client = createNotificacaoPollingClient({ intervalMs: 1_000, document: doc });
    const onChange = vi.fn();
    const verify = vi.fn(async () => true);

    const stop = client.start(onChange, verify);
    expect(onChange).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1_000);
    expect(verify).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledTimes(2);

    stop();
    await vi.advanceTimersByTimeAsync(5_000);
    expect(verify).toHaveBeenCalledTimes(1);
    expect(doc.listeners.size).toBe(0);
  });

  it('para quando a sessão deixa de valer', async () => {
    vi.useFakeTimers();
    const client = createNotificacaoPollingClient({ intervalMs: 1_000, document: new FakeDocument() });
    const onChange = vi.fn();
    const verify = vi.fn(async () => false);

    client.start(onChange, verify);
    await vi.advanceTimersByTimeAsync(3_000);
    expect(verify).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledTimes(1);
  });

  it('não consulta com a aba oculta e consulta ao voltar', async () => {
    vi.useFakeTimers();
    const doc = new FakeDocument();
    const client = createNotificacaoPollingClient({ intervalMs: 1_000, document: doc });
    const onChange = vi.fn();
    const verify = vi.fn(async () => true);

    client.start(onChange, verify);
    doc.hidden = true;
    await vi.advanceTimersByTimeAsync(3_000);
    expect(verify).not.toHaveBeenCalled();

    doc.setHidden(false);
    await vi.advanceTimersByTimeAsync(0);
    expect(verify).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledTimes(2);
  });

  it('um novo início encerra o anterior', async () => {
    vi.useFakeTimers();
    const client = createNotificacaoPollingClient({ intervalMs: 1_000, document: new FakeDocument() });
    const first = vi.fn(async () => true);
    const second = vi.fn(async () => true);

    client.start(() => undefined, first);
    client.start(() => undefined, second);
    await vi.advanceTimersByTimeAsync(1_000);
    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledTimes(1);
  });
});
