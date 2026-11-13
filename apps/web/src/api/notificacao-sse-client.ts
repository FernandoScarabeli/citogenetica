const SSE_URL = '/api/events/subscribe';
const RETRY_AFTER_LIMIT_MS = 30_000;
const INITIAL_RETRY_MS = 1_000;
const MAX_RETRY_MS = 30_000;
const EVENT_SOURCE_CLOSED = 2;

export interface NotificacaoEventSource {
  readonly readyState: number;
  onopen: ((event: Event) => void) | null;
  onerror: ((event: Event) => void) | null;
  addEventListener(type: string, listener: (event: Event) => void): void;
  close(): void;
}

export type NotificacaoEventSourceFactory = (
  url: string,
  options: EventSourceInit,
) => NotificacaoEventSource;

export interface NotificacaoSseClientOptions {
  createEventSource?: NotificacaoEventSourceFactory;
  setTimeout?: typeof globalThis.setTimeout;
  clearTimeout?: typeof globalThis.clearTimeout;
}

export interface NotificacaoSseClient {
  start(onInvalidated: () => void, verifySession: () => Promise<boolean>): () => void;
}

export function createNotificacaoSseClient(options: NotificacaoSseClientOptions = {}): NotificacaoSseClient {
  const scheduleTimeout = options.setTimeout ?? globalThis.setTimeout;
  const cancelTimeout = options.clearTimeout ?? globalThis.clearTimeout;
  const eventSourceAvailable = options.createEventSource !== undefined || typeof globalThis.EventSource !== 'undefined';
  const createEventSource = options.createEventSource ?? ((url, init) => new EventSource(url, init));

  let active = false;
  let source: NotificacaoEventSource | null = null;
  let retryTimer: ReturnType<typeof globalThis.setTimeout> | null = null;
  let retryAttempt = 0;
  let generation = 0;
  let onInvalidated: () => void = () => undefined;
  let verifySession: () => Promise<boolean> = async () => true;

  function clearRetry(): void {
    if (retryTimer === null) return;
    cancelTimeout(retryTimer);
    retryTimer = null;
  }

  function retryDelay(): number {
    const delay = Math.min(INITIAL_RETRY_MS * 2 ** retryAttempt, MAX_RETRY_MS);
    retryAttempt += 1;
    return delay;
  }

  function scheduleRetry(delay: number, currentGeneration: number): void {
    if (!active || currentGeneration !== generation || retryTimer !== null) return;
    retryTimer = scheduleTimeout(() => {
      retryTimer = null;
      connect(currentGeneration);
    }, delay);
  }

  function connect(currentGeneration: number): void {
    if (!active || currentGeneration !== generation || source !== null) return;

    let nextSource: NotificacaoEventSource;
    try {
      nextSource = createEventSource(SSE_URL, { withCredentials: true });
    } catch {
      scheduleRetry(retryDelay(), currentGeneration);
      return;
    }

    source = nextSource;
    nextSource.onopen = () => {
      if (!active || currentGeneration !== generation || source !== nextSource) return;
      retryAttempt = 0;
      onInvalidated();
    };
    nextSource.addEventListener('notification', () => {
      if (!active || currentGeneration !== generation || source !== nextSource) return;
      onInvalidated();
    });
    nextSource.onerror = () => {
      if (!active || currentGeneration !== generation || source !== nextSource) return;
      const refusedOrClosed = nextSource.readyState === EVENT_SOURCE_CLOSED;
      nextSource.close();
      source = null;

      if (!refusedOrClosed) {
        scheduleRetry(retryDelay(), currentGeneration);
        return;
      }

      void verifySession().then(sessionIsValid => {
        if (!active || currentGeneration !== generation) return;
        if (!sessionIsValid) {
          stop();
          return;
        }
        onInvalidated();
        scheduleRetry(Math.max(retryDelay(), RETRY_AFTER_LIMIT_MS), currentGeneration);
      }).catch(() => {
        scheduleRetry(Math.max(retryDelay(), RETRY_AFTER_LIMIT_MS), currentGeneration);
      });
    };
  }

  function stop(): void {
    if (!active) return;
    active = false;
    generation += 1;
    clearRetry();
    source?.close();
    source = null;
  }

  return {
    start(onChange, verify) {
      stop();
      if (!eventSourceAvailable) return () => undefined;
      active = true;
      generation += 1;
      retryAttempt = 0;
      onInvalidated = onChange;
      verifySession = verify;
      const currentGeneration = generation;
      connect(currentGeneration);
      return stop;
    },
  };
}

const POLLING_INTERVAL_MS = 30_000;

export interface NotificacaoPollingClientOptions {
  intervalMs?: number;
  setInterval?: typeof globalThis.setInterval;
  clearInterval?: typeof globalThis.clearInterval;
  document?: Pick<Document, 'hidden' | 'addEventListener' | 'removeEventListener'>;
}

/**
 * Alternativa ao SSE para ambientes cujo proxy não mantém conexões longas (ex.: Netlify + túnel
 * temporário da Cloudflare). Consulta a sessão a cada intervalo e avisa que as notificações podem
 * ter mudado; com a aba oculta não consulta, e consulta de novo assim que ela volta a aparecer.
 */
export function createNotificacaoPollingClient(options: NotificacaoPollingClientOptions = {}): NotificacaoSseClient {
  const intervalMs = options.intervalMs ?? POLLING_INTERVAL_MS;
  const startInterval = options.setInterval ?? globalThis.setInterval;
  const cancelInterval = options.clearInterval ?? globalThis.clearInterval;
  const doc = options.document ?? (typeof document === 'undefined' ? undefined : document);

  let stopCurrent: (() => void) | null = null;

  return {
    start(onChange, verifySession) {
      stopCurrent?.();
      let active = true;
      let checking = false;

      function tick(): void {
        if (!active || checking || doc?.hidden) return;
        checking = true;
        void verifySession()
          .then(valid => {
            if (!active) return;
            if (valid) onChange();
            else stop();
          })
          .catch(() => undefined)
          .finally(() => {
            checking = false;
          });
      }

      function onVisibilityChange(): void {
        if (!doc?.hidden) tick();
      }

      const timer = startInterval(tick, intervalMs);
      doc?.addEventListener('visibilitychange', onVisibilityChange);

      function stop(): void {
        if (!active) return;
        active = false;
        cancelInterval(timer);
        doc?.removeEventListener('visibilitychange', onVisibilityChange);
        if (stopCurrent === stop) stopCurrent = null;
      }

      stopCurrent = stop;
      onChange();
      return stop;
    },
  };
}

/** Escolhe SSE ou consulta periódica conforme `VITE_NOTIFICACOES_POR_CONSULTA` no build. */
export function createNotificacaoRealtimeClient(): NotificacaoSseClient {
  return import.meta.env.VITE_NOTIFICACOES_POR_CONSULTA === 'true'
    ? createNotificacaoPollingClient()
    : createNotificacaoSseClient();
}
