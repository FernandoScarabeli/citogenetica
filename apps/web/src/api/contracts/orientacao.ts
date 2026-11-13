export type EstadoConviteOrientacao = 'PENDENTE' | 'ACEITO' | 'RECUSADO' | 'REVOGADO' | 'EXPIRADO';

export interface VinculoOrientacaoResponseDTO {
  id: string;
  laboratorioId: string;
  orientadorUsuarioId: number;
  nomeOrientador: string;
  pesquisadorUsuarioId: number;
  nomePesquisador: string;
  criadoEm: string;
}

export interface ConviteOrientacaoResponseDTO {
  id: string;
  laboratorioId: string;
  emailDestino: string;
  estado: EstadoConviteOrientacao;
  expiraEm: string;
  criadoEm: string;
}

export interface CriarConviteOrientacaoRequestDTO {
  email: string;
}

export interface ConsumirConviteOrientacaoRequestDTO {
  token: string;
}
