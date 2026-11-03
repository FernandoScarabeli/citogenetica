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
