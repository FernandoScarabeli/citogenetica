export type EstadoLaboratorio = 'ATIVO' | 'DESATIVADO';

export type PapelLaboratorio = 'MEMBRO' | 'ADMIN' | 'ORIENTADOR';

export interface LaboratorioResponseDTO {
  id: string;
  nome: string;
  estado: EstadoLaboratorio;
  papeis: PapelLaboratorio[];
  versao: number;
}

export interface SalvarLaboratorioRequestDTO {
  nome: string;
}

export interface AlterarPapeisParticipacaoRequestDTO {
  versaoEsperada: number;
  papeis: PapelLaboratorio[];
}

export interface ParticipacaoResponseDTO {
  id: string;
  nome: string;
  email?: string | null;
  papeis: PapelLaboratorio[];
  versao: number;
}

export const PAPEL_LABEL: Record<PapelLaboratorio, string> = {
  MEMBRO: 'Membro',
  ADMIN: 'Administrador',
  ORIENTADOR: 'Orientador',
};
