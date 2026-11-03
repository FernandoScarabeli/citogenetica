export interface ErrorResponseDTO {
  timestamp?: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details?: string[];
}

export type FieldErrors = Record<string, string[]>;

export function mapFieldErrors(details: string[] | undefined): FieldErrors {
  const fields = new Map<string, string[]>();

  for (const detail of details ?? []) {
    const separator = detail.indexOf(':');
    if (separator < 1) continue;

    const field = detail.slice(0, separator).trim();
    const message = detail.slice(separator + 1).trim();
    if (!field) continue;

    fields.set(field, [...(fields.get(field) ?? []), message]);
  }

  return Object.fromEntries(fields);
}

export class ApiError extends Error {
  readonly status: number;
  readonly error: string;
  readonly path: string;
  readonly details?: string[];
  readonly timestamp?: string;
  readonly retryAfterSeconds?: number;

  constructor(response: ErrorResponseDTO, retryAfterSeconds?: number) {
    super(response.message);
    this.name = 'ApiError';
    this.status = response.status;
    this.error = response.error;
    this.path = response.path;
    this.details = response.details;
    this.timestamp = response.timestamp;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  get fieldErrors(): FieldErrors {
    return mapFieldErrors(this.details);
  }
}

export class NetworkError extends Error {
  constructor(cause?: unknown) {
    super('Não foi possível conectar à API. Verifique sua conexão e tente novamente.', { cause });
    this.name = 'NetworkError';
  }
}

export function isUnauthorized(error: unknown): error is ApiError {
  return error instanceof ApiError && error.status === 401;
}

export function isForbidden(error: unknown): error is ApiError {
  return error instanceof ApiError && error.status === 403;
}

export function isNotFound(error: unknown): error is ApiError {
  return error instanceof ApiError && error.status === 404;
}

export function isConflict(error: unknown): error is ApiError {
  return error instanceof ApiError && error.status === 409;
}

export function isRateLimited(error: unknown): error is ApiError {
  return error instanceof ApiError && error.status === 429;
}
