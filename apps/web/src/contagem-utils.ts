import { ApiError, NetworkError, isForbidden } from './api/errors';
import type { RegistroDTO } from './api/contracts/contagem';

/**
 * Instante que a tela de histórico mostra para um registro.
 *
 * A hora oficial é sempre a do servidor (`registradoEm`). Só há uma exceção: contagens antigas enviadas
 * do navegador chegam sem fuso (`fusoAparelho` nulo) mas com a hora em que a célula foi contada
 * (`horaAparelho`); nesse caso a data original é a que importa. Se o fuso existir, a contagem foi feita
 * com a tela já ligada ao servidor, e a hora do aparelho é apenas informativa.
 */
export function instanteExibido(registro: Pick<RegistroDTO, 'registradoEm' | 'horaAparelho' | 'fusoAparelho'>): string {
  if (registro.fusoAparelho == null && registro.horaAparelho) return registro.horaAparelho;
  return registro.registradoEm;
}

const diaFormat = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' });
const horaFormat = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' });

export const formatarData = (iso: string) => diaFormat.format(new Date(iso)).replace('.', '');
export const formatarHora = (iso: string) => horaFormat.format(new Date(iso));

/** Agrupa registros já ordenados por dia (calendário local), mantendo a ordem recebida. */
export function agruparPorDia<T extends Pick<RegistroDTO, 'registradoEm' | 'horaAparelho' | 'fusoAparelho'>>(registros: T[]): { dia: string; registros: T[] }[] {
  const grupos: { dia: string; registros: T[] }[] = [];
  for (const registro of registros) {
    const dia = formatarData(instanteExibido(registro));
    const ultimo = grupos[grupos.length - 1];
    if (ultimo && ultimo.dia === dia) ultimo.registros.push(registro);
    else grupos.push({ dia, registros: [registro] });
  }
  return grupos;
}

export const fusoDoAparelho = (): string | null => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || null;
  } catch {
    return null;
  }
};

export const MENSAGEM_SEM_CONEXAO = 'Esta célula não foi registrada. Verifique a internet e tente de novo.';

/** Texto de erro para o usuário. A mensagem da API já vem em português e é usada como está. */
export function mensagemDeErro(error: unknown, fallback: string): string {
  if (error instanceof NetworkError) return 'Sem conexão com o servidor. Verifique a internet e tente de novo.';
  if (isForbidden(error)) return 'Você não tem permissão para fazer esta ação.';
  if (error instanceof ApiError && error.message) return error.message;
  return fallback;
}

/**
 * Forma do nome de tipo de anormalidade usada para detectar duplicidade. Espelha `NomeTipoAnormalidade.normalizado`
 * do servidor: colapsa espaços, tira as pontas, minúsculas pt-BR e remove acentos.
 */
export function normalizarNomeTipo(nome: string): string {
  return nome.replace(/[\s\p{Z}\u0085]+/gu, ' ').trim().toLocaleLowerCase('pt-BR').normalize('NFD').replace(/\p{M}+/gu, '');
}

type RegistroDesfazivel = Pick<RegistroDTO, 'quantidade' | 'status'>;

function celulasDoRegistro(registro: RegistroDesfazivel): string {
  const plural = registro.quantidade !== 1;
  if (registro.status === 'NORMAL') return plural ? 'normais' : 'normal';
  return plural ? 'anormais' : 'anormal';
}

/** Texto do botão: diz o que será desfeito, por exemplo "Desfazer: 10 normais em Interfase". */
export function rotuloDesfazer(registro: RegistroDesfazivel, fase: string): string {
  return `Desfazer: ${registro.quantidade.toLocaleString('pt-BR')} ${celulasDoRegistro(registro)} em ${fase}`;
}

/** Frase da confirmação, por exemplo "10 células normais de Interfase". */
export function descricaoDesfazer(registro: RegistroDesfazivel, fase: string): string {
  const n = registro.quantidade;
  const celulas = registro.status === 'NORMAL'
    ? (n === 1 ? 'célula normal' : 'células normais')
    : (n === 1 ? 'célula anormal' : 'células anormais');
  return `${n.toLocaleString('pt-BR')} ${celulas} de ${fase}`;
}
