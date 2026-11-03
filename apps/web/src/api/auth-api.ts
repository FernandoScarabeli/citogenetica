import type {
  AtualizarMeuPerfilRequestDTO,
  AtualizarSenhaRequestDTO,
  LoginRequestDTO,
  RecuperarSenhaRequestDTO,
  RedefinirSenhaRequestDTO,
  UsuarioRequestDTO,
  UsuarioResponseDTO,
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

    register(data: UsuarioRequestDTO, options?: AuthApiOptions): Promise<UsuarioResponseDTO> {
      return client.request<UsuarioResponseDTO>('/api/v1/auth/register', {
        method: 'POST',
        json: data,
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

    getMe(options?: AuthApiOptions): Promise<UsuarioResponseDTO> {
      return client.request<UsuarioResponseDTO>('/api/v1/usuarios/me', { signal: options?.signal });
    },

    updateMe(data: AtualizarMeuPerfilRequestDTO, options?: AuthApiOptions): Promise<UsuarioResponseDTO> {
      return client.request<UsuarioResponseDTO>('/api/v1/usuarios/me', {
        method: 'PUT',
        json: data,
        signal: options?.signal,
      });
    },

    updateMyPassword(data: AtualizarSenhaRequestDTO, options?: AuthApiOptions): Promise<void> {
      return client.request<void>('/api/v1/usuarios/me/senha', {
        method: 'PUT',
        json: data,
        signal: options?.signal,
      });
    },
  };
}

export const authApi = createAuthApi();
