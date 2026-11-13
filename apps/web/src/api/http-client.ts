import {
  ApiError,
  NetworkError,
  type ErrorResponseDTO,
} from './errors.js';

function decodeExtended(value: string): string | undefined {
  // RFC 5987: charset'idioma'valor com percent-encoding.
  const match = /^([^']*)'[^']*'(.*)$/.exec(value);
  const raw = match ? match[2] : value;
  const charset = match ? match[1].toLowerCase() : 'utf-8';
  if (charset !== 'utf-8' && charset !== '') return undefined;
  try {
    return decodeURIComponent(raw);
  } catch {
    return undefined;
  }
}

function unquote(value: string): string {
  const trimmed = value.trim();
  if (trimmed.startsWith('"')) {
    const end = trimmed.lastIndexOf('"');
    return trimmed.slice(1, end > 0 ? end : undefined).replace(/\\(.)/g, '$1');
  }
  return trimmed;
}

/** Separa o nome sugerido pelo servidor, ignorando qualquer parte de caminho. */
function limparNome(name: string): string | undefined {
  const base = name.split(/[\\/]/).pop()?.replace(/[\u0000-\u001f]/g, '').trim();
  return base ? base : undefined;
}

/** Nome do arquivo em `Content-Disposition`: prefere `filename*` (UTF-8), depois `filename`. */
export function nomeDeContentDisposition(header: string | null): string | undefined {
  if (!header) return undefined;

  // Divide nos ";" que ficam fora de aspas.
  const parts = header.match(/(?:[^;"]|"(?:[^"\\]|\\.)*")+/g) ?? [];
  let simple: string | undefined;
  for (const part of parts.slice(1)) {
    const separator = part.indexOf('=');
    if (separator < 0) continue;
    const key = part.slice(0, separator).trim().toLowerCase();
    const value = part.slice(separator + 1);
    if (key === 'filename*') {
      const decoded = decodeExtended(value.trim());
      const name = decoded ? limparNome(decoded) : undefined;
      if (name) return name;
    } else if (key === 'filename' && simple === undefined) {
      simple = limparNome(unquote(value));
    }
  }
  return simple;
}

const CSRF_PATH = '/api/v1/auth/csrf';
const CSRF_ERROR_MESSAGE = 'Token CSRF ausente ou inválido.';
const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export interface HttpRequestOptions {
  method?: string;
  json?: unknown;
  headers?: HeadersInit;
  signal?: AbortSignal;
}

export interface HttpClientOptions {
  fetch?: typeof globalThis.fetch;
}

export interface ArquivoBaixado {
  blob: Blob;
  nome: string;
}

export interface HttpClient {
  request<T = void>(path: string, options?: HttpRequestOptions): Promise<T>;
  /** Baixa um arquivo (GET) com o nome sugerido pela API, ou `nomePadrao` quando ela não indica um. */
  download(path: string, nomePadrao: string, options?: Pick<HttpRequestOptions, 'signal'>): Promise<ArquivoBaixado>;
  renewCsrf(options?: Pick<HttpRequestOptions, 'signal'>): Promise<void>;
  onUnauthorized(listener: () => void): () => void;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isAbortError(error: unknown): boolean {
  return isRecord(error) && (error.name === 'AbortError' || error.name === 'TimeoutError');
}

function isJsonContentType(contentType: string | null): boolean {
  return contentType?.toLowerCase().includes('json') ?? false;
}

function parseRetryAfter(value: string | null): number | undefined {
  if (!value) return undefined;

  const normalized = value.trim();
  if (/^\d+$/.test(normalized)) return Number(normalized);

  const retryAt = Date.parse(normalized);
  if (Number.isNaN(retryAt)) return undefined;
  return Math.max(0, Math.ceil((retryAt - Date.now()) / 1000));
}

function readCookie(name: string): string | undefined {
  if (typeof document === 'undefined') return undefined;

  for (const part of document.cookie.split(';')) {
    const separator = part.indexOf('=');
    if (separator < 0 || part.slice(0, separator).trim() !== name) continue;

    const value = part.slice(separator + 1).trim();
    if (!value) return undefined;
    try {
      return decodeURIComponent(value);
    } catch {
      return value;
    }
  }

  return undefined;
}

function fallbackError(response: Response, path: string): ErrorResponseDTO {
  const reason = response.statusText || `Erro HTTP ${response.status}`;
  return {
    status: response.status,
    error: reason,
    message: 'Não foi possível concluir a solicitação.',
    path,
  };
}

async function createApiError(response: Response, path: string): Promise<ApiError> {
  let body: unknown;
  if (response.body !== null && isJsonContentType(response.headers.get('content-type'))) {
    try {
      body = await response.json();
    } catch {
      body = undefined;
    }
  }

  const fallback = fallbackError(response, path);
  const dto = isRecord(body) ? body : {};
  const errorResponse: ErrorResponseDTO = {
    timestamp: typeof dto.timestamp === 'string' ? dto.timestamp : undefined,
    status: response.status,
    error: typeof dto.error === 'string' ? dto.error : fallback.error,
    message: typeof dto.message === 'string' ? dto.message : fallback.message,
    path: typeof dto.path === 'string' ? dto.path : fallback.path,
    details: Array.isArray(dto.details)
      ? dto.details.filter((detail): detail is string => typeof detail === 'string')
      : undefined,
  };

  const retryAfterSeconds = errorResponse.status === 429
    ? parseRetryAfter(response.headers.get('retry-after'))
    : undefined;
  return new ApiError(errorResponse, retryAfterSeconds);
}

function isCsrfRejection(error: unknown): error is ApiError {
  return error instanceof ApiError && error.status === 403 && error.message === CSRF_ERROR_MESSAGE;
}

export function createHttpClient(options: HttpClientOptions = {}): HttpClient {
  const fetcher = options.fetch ?? ((input, init) => globalThis.fetch(input, init));
  const unauthorizedListeners = new Set<() => void>();
  let csrfRequest: Promise<void> | undefined;

  function notifyUnauthorized(): void {
    for (const listener of unauthorizedListeners) {
      try {
        listener();
      } catch {
        // Um assinante não deve impedir que a resposta 401 chegue a quem fez a chamada.
      }
    }
  }

  async function fetchResponse(path: string, init: RequestInit): Promise<Response> {
    try {
      return await fetcher(path, init);
    } catch (error) {
      if (isAbortError(error) || init.signal?.aborted) throw error;
      throw new NetworkError(error);
    }
  }

  async function throwIfUnauthorized(response: Response, path: string): Promise<ApiError> {
    const error = await createApiError(response, path);
    if (error.status === 401) notifyUnauthorized();
    return error;
  }

  function requestCsrf(signal?: AbortSignal): Promise<void> {
    const request = (async () => {
      const response = await fetchResponse(CSRF_PATH, {
        method: 'GET',
        credentials: 'include',
        headers: { Accept: 'application/json' },
        signal,
      });

      if (!response.ok) throw await throwIfUnauthorized(response, CSRF_PATH);
      if (!readCookie('XSRF-TOKEN')) {
        throw new Error('A API não disponibilizou o token de segurança necessário.');
      }
    })();

    csrfRequest = request;
    void request.finally(() => {
      if (csrfRequest === request) csrfRequest = undefined;
    }).catch(() => undefined);
    return request;
  }

  async function ensureCsrf(signal?: AbortSignal): Promise<void> {
    if (readCookie('XSRF-TOKEN')) return;
    if (csrfRequest) return csrfRequest;
    await requestCsrf(signal);
  }

  async function renewCsrf(requestOptions: Pick<HttpRequestOptions, 'signal'> = {}): Promise<void> {
    if (csrfRequest) await csrfRequest;
    await requestCsrf(requestOptions.signal);
  }

  async function request<T = void>(path: string, requestOptions: HttpRequestOptions = {}): Promise<T> {
    const method = (requestOptions.method ?? 'GET').toUpperCase();
    const mutating = MUTATING_METHODS.has(method);
    const headers = new Headers(requestOptions.headers);
    headers.set('Accept', 'application/json');

    const hasJson = Object.prototype.hasOwnProperty.call(requestOptions, 'json');
    const body = hasJson ? JSON.stringify(requestOptions.json) : undefined;
    if (hasJson) headers.set('Content-Type', 'application/json');

    if (mutating) {
      await ensureCsrf(requestOptions.signal);
      const csrfToken = readCookie('XSRF-TOKEN');
      if (!csrfToken) throw new Error('O token de segurança expirou. Tente novamente.');
      headers.set('X-XSRF-TOKEN', csrfToken);
    }

    const init: RequestInit = {
      method,
      credentials: 'include',
      signal: requestOptions.signal,
      ...(body === undefined ? {} : { body }),
    };

    let response = await fetchResponse(path, { ...init, headers: new Headers(headers) });
    if (mutating && response.status === 403) {
      const firstError = await createApiError(response.clone(), path);
      if (isCsrfRejection(firstError)) {
        await renewCsrf({ signal: requestOptions.signal });
        const csrfToken = readCookie('XSRF-TOKEN');
        if (!csrfToken) throw new Error('A API não disponibilizou o token de segurança necessário.');
        headers.set('X-XSRF-TOKEN', csrfToken);
        response = await fetchResponse(path, { ...init, headers: new Headers(headers) });
      } else {
        throw firstError;
      }
    }

    if (!response.ok) throw await throwIfUnauthorized(response, path);
    if (response.status === 204 || response.body === null) return undefined as T;

    if (!isJsonContentType(response.headers.get('content-type'))) return undefined as T;
    try {
      return await response.json() as T;
    } catch {
      throw new Error('A API retornou uma resposta JSON inválida.');
    }
  }

  async function download(path: string, nomePadrao: string, requestOptions: Pick<HttpRequestOptions, 'signal'> = {}): Promise<ArquivoBaixado> {
    const response = await fetchResponse(path, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: '*/*' },
      signal: requestOptions.signal,
    });
    if (!response.ok) throw await throwIfUnauthorized(response, path);
    const blob = await response.blob();
    return { blob, nome: nomeDeContentDisposition(response.headers.get('content-disposition')) ?? nomePadrao };
  }

  return {
    request,
    download,
    renewCsrf,
    onUnauthorized(listener) {
      unauthorizedListeners.add(listener);
      return () => unauthorizedListeners.delete(listener);
    },
  };
}

export const httpClient = createHttpClient();
