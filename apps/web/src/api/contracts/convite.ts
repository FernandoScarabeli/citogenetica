import type { PapelLaboratorio } from './laboratorio';

export type EstadoConviteLaboratorio = 'PENDENTE' | 'ACEITO' | 'RECUSADO' | 'REVOGADO' | 'EXPIRADO';

export interface CriarConviteLaboratorioRequestDTO {
  email: string;
  papeis: PapelLaboratorio[];
}

export interface ConsumirConviteRequestDTO {
  token: string;
}

export interface ConviteLaboratorioResponseDTO {
  id: string;
  laboratorioId: string;
  emailDestino: string;
  papeis: PapelLaboratorio[];
  estado: EstadoConviteLaboratorio;
  expiraEm: string;
  criadoEm: string;
}
