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

/** O que a tela do convite mostra antes do login. */
export interface PreviaConviteResponseDTO {
  laboratorioNome: string;
  convidadoPorNome: string;
  emailMascarado: string;
  /** Vazio em convite de orientação. */
  papeis: PapelLaboratorio[];
  estado: 'PENDENTE' | 'EXPIRADO';
  /** Só a API sabe se o convite é para a conta aberta; vem nulo sem sessão. */
  paraContaAtual: boolean | null;
}
