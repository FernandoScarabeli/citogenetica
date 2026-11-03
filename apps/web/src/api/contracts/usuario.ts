export type RolesEnum = 'ADMIN' | 'USER';
export type StatusEnum = 'ATIVO' | 'INATIVO';

export interface AtualizarMeuPerfilRequestDTO {
  nome: string;
}

export interface AtualizarSenhaRequestDTO {
  senhaAtual: string;
  novaSenha: string;
}

export interface SolicitarTrocaEmailRequestDTO {
  novoEmail: string;
  senhaAtual: string;
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
