export type RolesEnum = 'ADMIN' | 'USER';
export type StatusEnum = 'ATIVO' | 'INATIVO';

export interface LoginRequestDTO {
  email: string;
  senha: string;
}

export interface UsuarioRequestDTO {
  nome: string;
  email: string;
  senha: string;
  roleName: string;
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

export interface AtualizarMeuPerfilRequestDTO {
  nome: string;
}

export interface AtualizarSenhaRequestDTO {
  senhaAtual: string;
  novaSenha: string;
}

export interface RoleDTO {
  id: number;
  version: number;
  createdDate: string;
  lastModifiedDate: string | null;
  nome: RolesEnum;
}

export interface UsuarioResponseDTO {
  id: number;
  version: number;
  createdDate: string;
  lastModifiedDate: string | null;
  nome: string;
  email: string;
  status: StatusEnum;
  role: RoleDTO;
}
