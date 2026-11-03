export interface LoginRequestDTO {
  email: string;
  senha: string;
}

export interface UsuarioRequestDTO {
  nome: string;
  email: string;
  senha: string;
}

export interface RecuperarSenhaRequestDTO {
  email: string;
}

export interface RedefinirSenhaRequestDTO {
  email: string;
  codigo: string;
  novaSenha: string;
}

export interface ValidarCodigoRequestDTO {
  email: string;
  codigo: string;
}

export type { RoleDTO, RolesEnum, StatusEnum, UsuarioResponseDTO } from './usuario.js';
