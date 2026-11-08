import type { EstadoLaboratorio } from './laboratorio';

export interface AdminAdministradorDTO {
  nome: string;
  email: string;
}

export interface AdminLaboratorioResponseDTO {
  id: string;
  nome: string;
  estado: EstadoLaboratorio;
  quantidadeMembrosAtivos: number;
  administradores: AdminAdministradorDTO[];
}

export interface AdminLaboratorioPageDTO {
  content: AdminLaboratorioResponseDTO[];
  currentPage: number;
  totalPages: number;
  totalElements: number;
}
