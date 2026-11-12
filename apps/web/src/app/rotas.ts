import { matchPath } from 'react-router';

/** Abas da tela de gerenciamento, como aparecem em `?aba=` na URL. */
export const ABAS_GERENCIAR = ['membros', 'orientacao', 'convites', 'configuracoes'] as const;
export type AbaGerenciar = (typeof ABAS_GERENCIAR)[number];

export const CAMINHO_LABORATORIOS = '/laboratorios';
export const CAMINHO_PERFIL = '/perfil';

/** Lista de lâminas de um laboratório, ou das lâminas avulsas quando não há laboratório. */
export function caminhoLaminas(laboratorioId: string | null): string {
  return laboratorioId ? `/laboratorios/${encodeURIComponent(laboratorioId)}/laminas` : '/laminas-avulsas';
}

export function caminhoLamina(laboratorioId: string | null, laminaId: string): string {
  return `${caminhoLaminas(laboratorioId)}/${encodeURIComponent(laminaId)}`;
}

export function caminhoGerenciar(laboratorioId: string): string {
  return `/laboratorios/${encodeURIComponent(laboratorioId)}/gerenciar`;
}

export type TelaDaRota =
  | { tela: 'lista' }
  | { tela: 'perfil' }
  | { tela: 'laminas'; laboratorioId: string | null; laminaId: string | null }
  | { tela: 'gerenciar'; laboratorioId: string };

/** Descobre qual tela o endereço representa. Endereços desconhecidos caem na lista de laboratórios. */
export function telaDaRota(pathname: string): TelaDaRota {
  if (matchPath({ path: CAMINHO_PERFIL, end: true }, pathname)) return { tela: 'perfil' };
  const gerenciar = matchPath({ path: '/laboratorios/:laboratorioId/gerenciar', end: true }, pathname);
  if (gerenciar?.params.laboratorioId) return { tela: 'gerenciar', laboratorioId: gerenciar.params.laboratorioId };
  const laminasLab = matchPath({ path: '/laboratorios/:laboratorioId/laminas/:laminaId?', end: true }, pathname);
  if (laminasLab?.params.laboratorioId) {
    return { tela: 'laminas', laboratorioId: laminasLab.params.laboratorioId, laminaId: laminasLab.params.laminaId ?? null };
  }
  const avulsas = matchPath({ path: '/laminas-avulsas/:laminaId?', end: true }, pathname);
  if (avulsas) return { tela: 'laminas', laboratorioId: null, laminaId: avulsas.params.laminaId ?? null };
  return { tela: 'lista' };
}

/** Lê a aba pedida na URL; valor ausente ou desconhecido vira "membros". */
export function abaDaBusca(search: string): AbaGerenciar {
  const aba = new URLSearchParams(search).get('aba');
  return ABAS_GERENCIAR.find(item => item === aba) ?? 'membros';
}
