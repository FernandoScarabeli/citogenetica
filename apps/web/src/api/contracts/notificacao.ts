export type TipoNotificacao = 'INFO' | 'WARNING' | 'SUCCESS' | 'ERROR';

export interface NotificacaoResponseDTO {
  id: number;
  tipo: TipoNotificacao;
  titulo: string;
  mensagem: string | null;
  dados: Record<string, unknown> | null;
  lida: boolean;
  dataCriacao: string;
}

export interface NotificacaoPageDTO {
  content: NotificacaoResponseDTO[];
  number: number;
  totalPages: number;
  last: boolean;
}

export interface NotificacaoCountDTO {
  count: number;
}

export interface MarcarTodasLidasResponseDTO {
  marcadas: number;
}
