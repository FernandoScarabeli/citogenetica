import type {
  LoginRequestDTO,
  RecuperarSenhaRequestDTO,
  RedefinirSenhaRequestDTO,
  UsuarioRequestDTO,
  ValidarCodigoRequestDTO,
} from './contracts/auth.js';
import { httpClient, type HttpClient } from './http-client.js';

export interface AuthApiOptions {
  signal?: AbortSignal;
}

export function createAuthApi(client: HttpClient = httpClient) {
  return {
    getCsrf(options?: AuthApiOptions): Promise<void> {
      return client.renewCsrf(options);
    },

    async login(data: LoginRequestDTO, options?: AuthApiOptions): Promise<void> {
      await client.request<void>('/api/v1/auth/login', {
        method: 'POST',
        json: data,
        signal: options?.signal,
      });
      await client.renewCsrf(options);
    },

    async logout(options?: AuthApiOptions): Promise<void> {
      await client.request<void>('/api/v1/auth/logout', {
        method: 'POST',
        signal: options?.signal,
      });
      await client.renewCsrf(options);
    },

    register(data: UsuarioRequestDTO, options?: AuthApiOptions): Promise<void> {
      return client.request<void>('/api/v1/auth/register', {
        method: 'POST',
        json: data,
        signal: options?.signal,
      });
    },

    resendEmailConfirmation(email: string, options?: AuthApiOptions): Promise<void> {
      return client.request<void>('/api/v1/auth/reenviar-confirmacao', {
        method: 'POST',
        json: { email },
        signal: options?.signal,
      });
    },

    confirmEmail(token: string, options?: AuthApiOptions): Promise<void> {
      return client.request<void>('/api/v1/auth/confirmar-email', {
        method: 'POST',
        json: { token },
        signal: options?.signal,
      });
    },

    confirmEmailChange(token: string, options?: AuthApiOptions): Promise<void> {
      return client.request<void>('/api/v1/auth/confirmar-troca-email', {
        method: 'POST',
        json: { token },
        signal: options?.signal,
      });
    },

    requestPasswordRecovery(data: RecuperarSenhaRequestDTO, options?: AuthApiOptions): Promise<void> {
      return client.request<void>('/api/v1/auth/recuperar-senha', {
        method: 'POST',
        json: data,
        signal: options?.signal,
      });
    },

    validateRecoveryCode(data: ValidarCodigoRequestDTO, options?: AuthApiOptions): Promise<void> {
      return client.request<void>('/api/v1/auth/validar-codigo', {
        method: 'POST',
        json: data,
        signal: options?.signal,
      });
    },

    resetPassword(data: RedefinirSenhaRequestDTO, options?: AuthApiOptions): Promise<void> {
      return client.request<void>('/api/v1/auth/redefinir-senha', {
        method: 'POST',
        json: data,
        signal: options?.signal,
      });
    },
  };
}

export const authApi = createAuthApi();
