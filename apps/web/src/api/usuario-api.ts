import type {
  AtualizarMeuPerfilRequestDTO,
  AtualizarSenhaRequestDTO,
  SolicitarTrocaEmailRequestDTO,
  UsuarioResponseDTO,
} from './contracts/usuario.js';
import { httpClient, type HttpClient } from './http-client.js';

/** Endereço da foto de perfil, ou nada quando a conta não tem foto. O `v` muda a cada foto nova. */
export function fotoPerfilUrl(usuario: Pick<UsuarioResponseDTO, 'fotoVersao'>): string | undefined {
  return usuario.fotoVersao ? `/api/v1/usuarios/me/foto?v=${encodeURIComponent(usuario.fotoVersao)}` : undefined;
}

export interface UsuarioApiOptions {
  signal?: AbortSignal;
}

export function createUsuarioApi(client: HttpClient = httpClient) {
  return {
    getMe(options?: UsuarioApiOptions): Promise<UsuarioResponseDTO> {
      return client.request<UsuarioResponseDTO>('/api/v1/usuarios/me', { signal: options?.signal });
    },

    updateMyProfile(data: AtualizarMeuPerfilRequestDTO, options?: UsuarioApiOptions): Promise<UsuarioResponseDTO> {
      return client.request<UsuarioResponseDTO>('/api/v1/usuarios/me', {
        method: 'PUT',
        json: data,
        signal: options?.signal,
      });
    },

    updateMyPassword(data: AtualizarSenhaRequestDTO, options?: UsuarioApiOptions): Promise<void> {
      return client.request<void>('/api/v1/usuarios/me/senha', {
        method: 'PUT',
        json: data,
        signal: options?.signal,
      });
    },

    updateMyPhoto(arquivo: File, options?: UsuarioApiOptions): Promise<UsuarioResponseDTO> {
      const form = new FormData();
      form.append('arquivo', arquivo);
      return client.request<UsuarioResponseDTO>('/api/v1/usuarios/me/foto', {
        method: 'PUT',
        form,
        signal: options?.signal,
      });
    },

    requestEmailChange(data: SolicitarTrocaEmailRequestDTO, options?: UsuarioApiOptions): Promise<void> {
      return client.request<void>('/api/v1/usuarios/me/email', {
        method: 'POST',
        json: data,
        signal: options?.signal,
      });
    },
  };
}

export const usuarioApi = createUsuarioApi();
