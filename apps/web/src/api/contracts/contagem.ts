export type StatusRegistro = 'NORMAL' | 'ANORMAL';

export interface PageDTO<T> {
  content: T[];
  currentPage: number;
  totalPages: number;
  totalElements: number;
}

export interface TipoAnormalidadeFixoDTO {
  id: string;
  nome: string;
}

export interface FaseProtocoloDTO {
  id: string;
  nome: string;
  ordem: number;
  contaComoDivisao: boolean;
  tipos: TipoAnormalidadeFixoDTO[];
}

export interface VersaoProtocoloDTO {
  id: string;
  numero: number;
  fases: FaseProtocoloDTO[];
}

export interface ProtocoloDTO {
  id: string;
  codigo: string;
  nome: string;
  versaoAtual: VersaoProtocoloDTO;
}

export interface TipoPersonalizadoDTO {
  id: string;
  faseId: string;
  laboratorioId: string | null;
  nome: string;
}

export interface CriarTipoPersonalizadoRequestDTO {
  id: string;
  faseId: string;
  laboratorioId?: string | null;
  nome: string;
}

export interface LaminaDTO {
  id: string;
  versao: number;
  donoUsuarioId: number;
  donoNome: string;
  laboratorioId: string | null;
  protocoloVersaoId: string;
  nome: string;
  especie: string;
  tratamento: string;
  observacoes: string;
  meta: number | null;
  totalCelulas: number;
  criadaEm: string;
  atualizadaEm: string;
}

export interface CriarLaminaRequestDTO {
  id: string;
  protocoloVersaoId: string;
  laboratorioId?: string | null;
  nome: string;
  especie?: string | null;
  tratamento?: string | null;
  observacoes?: string | null;
  meta?: number | null;
  /** Instante original de criação (ISO 8601); ausente = momento do envio. */
  criadaEm?: string;
}

export interface AtualizarLaminaRequestDTO {
  versaoEsperada: number;
  nome: string;
  especie?: string | null;
  tratamento?: string | null;
  observacoes?: string | null;
  meta?: number | null;
}

export interface CriarRegistroRequestDTO {
  id: string;
  faseId: string;
  status: StatusRegistro;
  tipoIds?: string[];
  quantidade: number;
  horaAparelho?: string | null;
  fusoAparelho?: string | null;
}

export interface AtualizarRegistroRequestDTO {
  versaoEsperada: number;
  faseId: string;
  status: StatusRegistro;
  tipoIds?: string[];
  quantidade: number;
}

export interface DesfazerRegistroRequestDTO {
  id: string;
  faseId: string;
  status: StatusRegistro;
  tipoIds?: string[];
  quantidade: number;
}

export interface RegistroDTO {
  id: string;
  versao: number;
  laminaId: string;
  faseId: string;
  status: StatusRegistro;
  tipoIds: string[];
  quantidade: number;
  registradoEm: string;
  horaAparelho: string | null;
  fusoAparelho: string | null;
}

export interface TipoDoRegistroDTO {
  id: string;
  nome: string;
}

export interface RegistroHistoricoDTO extends RegistroDTO {
  tipos: TipoDoRegistroDTO[];
}

export interface DesfazerRegistroResponseDTO {
  id: string;
  quantidadeDesfeita: number;
  registros: { id: string; quantidadeRestante: number; excluido: boolean }[];
}

export interface ResumoTipoDTO {
  tipoId: string;
  nome: string;
  contagem: number;
  percentual: number;
}

export interface ResumoFaseDTO {
  faseId: string;
  nome: string;
  ordem: number;
  normais: number;
  anormais: number;
  total: number;
  percentualAnormais: number;
  tipos: ResumoTipoDTO[];
}

export interface ResumoLaminaDTO {
  laminaId: string;
  total: number;
  anormais: number;
  indiceMitotico: number;
  fases: ResumoFaseDTO[];
}
