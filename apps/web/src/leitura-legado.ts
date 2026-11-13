import type { ProtocoloDTO } from './api/contracts/contagem';
import { normalizarNomeTipo } from './contagem-utils';
import type { Entry, Protocol } from './domain';

/**
 * Leitura dos backups JSON baixados pelos HTMLs antigos (registro-meiose.html e registro-Ciclo_celular.html).
 * Módulo puro: não fala com a API nem com o navegador. Nada é descartado em silêncio: o que não puder ser
 * importado volta em `recusadas`, com o motivo.
 */

/** Tamanho máximo aceito para o arquivo. Um backup real tem poucas dezenas de KB. */
export const LIMITE_BYTES = 10 * 1024 * 1024;

/** Mesmos limites que a API aplica às lâminas. */
const LIMITE_NOME = 120;
const LIMITE_OBSERVACOES = 5000;
/** O HTML da mitose usava uma meta fixa de 1000 células; o da meiose não tinha meta. */
export const META_MITOSE_LEGADA = 1000;

/** Fases de cada protocolo, na grafia dos HTMLs antigos (idêntica à do protocolo do sistema). */
const FASES: Record<Protocol, string[]> = {
  meiose: ['Leptóteno/Zigóteno', 'Paquíteno', 'Diplóteno/Diacinese', 'Metáfase I', 'Anáfase I', 'Telófase I', 'Prófase II', 'Metáfase II', 'Anáfase II', 'Telófase II', 'Tétrade'],
  mitose: ['Interfase', 'Prófase', 'Metáfase', 'Anáfase', 'Telófase'],
};

/**
 * Nomes de tipos que o HTML antigo escrevia de outro jeito que o sistema (seed V12 do protocolo). Chave: nome antigo
 * normalizado (sem acento, minúsculo, espaços colapsados). Só a mitose tinha diferenças; os tipos da meiose coincidem.
 */
const NOMES_ANTIGOS: Record<string, string> = {
  [normalizarNomeTipo('Stickness')]: 'Stickiness',
  [normalizarNomeTipo('Micronúcleo: +de 3')]: 'Micronúcleo: mais de 3',
  [normalizarNomeTipo('Pontes com fragmento')]: 'Ponte com fragmento',
};

export type CelulaLegada = {
  /** `id` do item de `history` no arquivo. Não é único entre arquivos: o id no sistema também usa nome e hora. */
  idHistorico: string;
  t: number;
  fase: string;
  status: Entry['status'];
  /** Nome do tipo já no vocabulário do sistema; vazio nas células normais. */
  tipo: string;
};

export type LaminaLegada = {
  nome: string;
  protocolo: Protocol;
  especie: string;
  tratamento: string;
  observacoes: string;
  /** Da mais antiga para a mais recente. */
  celulas: CelulaLegada[];
  /** Nomes de tipos trocados pelos nomes atuais do sistema. */
  renomeados: { de: string; para: string }[];
};

export type LeituraLegado = {
  laminas: LaminaLegada[];
  recusadas: { nome: string; motivo: string }[];
};

/** Problema do arquivo como um todo; a mensagem já é para a pessoa. */
export class ErroDeArquivoLegado extends Error {}

const ehObjeto = (valor: unknown): valor is Record<string, unknown> => typeof valor === 'object' && valor !== null && !Array.isArray(valor);
const texto = (valor: unknown): string => (typeof valor === 'string' ? valor : '');
const rotulo = (nome: string) => `"${nome}"`;

function tiposDeAnormalidade(fase: unknown): Record<string, unknown> {
  return ehObjeto(fase) && ehObjeto(fase.abnormalTypes) ? fase.abnormalTypes : {};
}

function contarCelulasDoResumo(phases: Record<string, unknown>): number | null {
  let total = 0;
  for (const fase of Object.values(phases)) {
    if (!ehObjeto(fase)) return null;
    const normal = fase.normal ?? 0;
    if (typeof normal !== 'number' || !Number.isInteger(normal) || normal < 0) return null;
    total += normal;
    for (const quantidade of Object.values(tiposDeAnormalidade(fase))) {
      if (typeof quantidade !== 'number' || !Number.isInteger(quantidade) || quantidade < 0) return null;
      total += quantidade;
    }
  }
  return total;
}

function totalDaFase(fase: unknown): number {
  if (!ehObjeto(fase)) return 0;
  const normais = typeof fase.normal === 'number' ? fase.normal : 0;
  return normais + Object.values(tiposDeAnormalidade(fase)).reduce<number>((soma, valor) => soma + (typeof valor === 'number' ? valor : 0), 0);
}

type Convertida = { lamina: LaminaLegada } | { motivo: string };

function converterLamina(nome: string, bruta: unknown): Convertida {
  if (!ehObjeto(bruta)) return { motivo: 'O formato desta lâmina não é o do backup antigo.' };
  const nomeLimpo = nome.replace(/\s+/g, ' ').trim();
  if (!nomeLimpo) return { motivo: 'A lâmina não tem nome.' };
  if (nomeLimpo.length > LIMITE_NOME) return { motivo: `O nome tem mais de ${LIMITE_NOME} caracteres.` };
  const { phases, history } = bruta;
  if (!ehObjeto(phases) || !Array.isArray(history)) return { motivo: 'Faltam as contagens (phases) ou o histórico (history) da lâmina.' };
  if (history.length === 0) return { motivo: 'A lâmina não tem nenhuma célula registrada.' };

  const observacoes = texto(bruta.obs);
  if (observacoes.length > LIMITE_OBSERVACOES) return { motivo: `As observações têm mais de ${LIMITE_OBSERVACOES} caracteres.` };
  const especie = texto(bruta.especie);
  const tratamento = texto(bruta.tratamento);
  if (especie.length > LIMITE_NOME || tratamento.length > LIMITE_NOME) return { motivo: `A espécie ou o tratamento tem mais de ${LIMITE_NOME} caracteres.` };

  // O arquivo não diz o protocolo: ele sai das fases usadas (no histórico e nas contagens).
  const usadas = new Set<string>();
  for (const [fase, valor] of Object.entries(phases)) if (totalDaFase(valor) > 0) usadas.add(fase);
  for (const item of history) if (ehObjeto(item) && typeof item.phase === 'string') usadas.add(item.phase);
  const desconhecidas = [...usadas].filter(fase => !FASES.meiose.includes(fase) && !FASES.mitose.includes(fase));
  if (desconhecidas.length > 0) return { motivo: `Fase desconhecida: ${desconhecidas.map(rotulo).join(', ')}. Não é possível saber a que protocolo a lâmina pertence.` };
  const protocolos = (['meiose', 'mitose'] as const).filter(codigo => [...usadas].some(fase => FASES[codigo].includes(fase)));
  if (protocolos.length !== 1) return { motivo: 'As fases da lâmina misturam meiose e mitose.' };
  const protocolo = protocolos[0];

  // Conferência: as contagens por fase precisam bater com as células do histórico.
  const resumo = contarCelulasDoResumo(phases);
  if (resumo === null) return { motivo: 'As contagens por fase estão em formato inválido.' };
  if (resumo !== history.length) {
    return { motivo: `As contagens somam ${resumo} células, mas o histórico tem ${history.length}. Para não importar números errados, a lâmina foi deixada de fora.` };
  }

  const celulas: CelulaLegada[] = [];
  const renomeados = new Map<string, string>();
  const vistas = new Set<string>();
  for (const [posicao, item] of history.entries()) {
    const quando = `Célula ${posicao + 1} do histórico`;
    if (!ehObjeto(item)) return { motivo: `${quando} está em formato inválido.` };
    const { id, phase, status, type, t } = item;
    if (typeof id !== 'string' || !id) return { motivo: `${quando} não tem identificador.` };
    if (typeof phase !== 'string' || !FASES[protocolo].includes(phase)) return { motivo: `${quando} tem uma fase que não pertence ao protocolo de ${protocolo}.` };
    if (status !== 'Normal' && status !== 'Anormal') return { motivo: `${quando} tem uma situação desconhecida (esperado Normal ou Anormal).` };
    if (typeof t !== 'number' || !Number.isSafeInteger(t) || Number.isNaN(new Date(t).getTime())) return { motivo: `${quando} não tem data e hora válidas.` };
    if (vistas.has(`${id}|${t}`)) return { motivo: `O identificador de célula ${rotulo(id)} aparece mais de uma vez.` };
    vistas.add(`${id}|${t}`);

    let tipo = '';
    if (status === 'Anormal') {
      const original = typeof type === 'string' ? type : '';
      tipo = NOMES_ANTIGOS[normalizarNomeTipo(original)] ?? original;
      if (tipo !== original) renomeados.set(original, tipo);
    }
    celulas.push({ idHistorico: id, t, fase: phase, status: status === 'Normal' ? 'normal' : 'anormal', tipo });
  }
  celulas.sort((a, b) => a.t - b.t);

  return {
    lamina: {
      nome: nomeLimpo, protocolo, especie: especie.trim(), tratamento: tratamento.trim(), observacoes, celulas,
      renomeados: [...renomeados].map(([de, para]) => ({ de, para })),
    },
  };
}

/** Interpreta o texto de um backup. Lança `ErroDeArquivoLegado` se o arquivo não for um backup utilizável. */
export function interpretarBackupLegado(conteudo: string): LeituraLegado {
  if (conteudo.length > LIMITE_BYTES) throw new ErroDeArquivoLegado('O arquivo é grande demais para ser um backup das lâminas (limite de 10 MB).');
  if (!conteudo.trim()) throw new ErroDeArquivoLegado('O arquivo está vazio.');
  let bruto: unknown;
  try {
    bruto = JSON.parse(conteudo);
  } catch {
    throw new ErroDeArquivoLegado('Não foi possível ler o arquivo: ele não é um JSON válido. Escolha o arquivo de backup baixado do registro antigo.');
  }
  if (!ehObjeto(bruto) || !ehObjeto(bruto.laminas)) {
    throw new ErroDeArquivoLegado('Este arquivo não parece um backup do registro antigo: não há lâminas nele.');
  }
  const entradas = Object.entries(bruto.laminas);
  if (entradas.length === 0) throw new ErroDeArquivoLegado('O backup não tem nenhuma lâmina.');

  const resultado: LeituraLegado = { laminas: [], recusadas: [] };
  const nomes = new Set<string>();
  for (const [nome, valor] of entradas) {
    const convertida = converterLamina(nome, valor);
    if ('motivo' in convertida) {
      resultado.recusadas.push({ nome, motivo: convertida.motivo });
      continue;
    }
    // Nomes que só diferem em espaços viram a mesma lâmina no sistema; manter as duas misturaria as células.
    const chave = `${convertida.lamina.protocolo}|${convertida.lamina.nome}`;
    if (nomes.has(chave)) {
      resultado.recusadas.push({ nome, motivo: 'Já há outra lâmina com este nome (diferindo só nos espaços) neste arquivo.' });
      continue;
    }
    nomes.add(chave);
    resultado.laminas.push(convertida.lamina);
  }
  return resultado;
}

/** Lê o arquivo escolhido pela pessoa, checando o tamanho antes de carregá-lo. */
export async function lerBackupLegado(arquivo: { size: number; text: () => Promise<string> }): Promise<LeituraLegado> {
  if (arquivo.size > LIMITE_BYTES) throw new ErroDeArquivoLegado('O arquivo é grande demais para ser um backup das lâminas (limite de 10 MB).');
  let conteudo: string;
  try {
    conteudo = await arquivo.text();
  } catch {
    throw new ErroDeArquivoLegado('Não foi possível abrir o arquivo. Tente escolher de novo.');
  }
  return interpretarBackupLegado(conteudo);
}

/** Tipos da lâmina que não existem no protocolo atual e serão criados como tipos personalizados. */
export function tiposPersonalizados(lamina: LaminaLegada, protocolos: ProtocoloDTO[]): string[] {
  const versao = protocolos.find(item => item.codigo === lamina.protocolo)?.versaoAtual;
  if (!versao) return [];
  const novos = new Map<string, string>();
  for (const celula of lamina.celulas) {
    if (celula.status !== 'anormal') continue;
    const fase = versao.fases.find(item => item.nome === celula.fase);
    const chave = normalizarNomeTipo(celula.tipo || 'Não informado');
    if (!fase || fase.tipos.some(tipo => normalizarNomeTipo(tipo.nome) === chave)) continue;
    // Vale a primeira grafia (a mais antiga), a mesma que o envio usa ao criar o tipo.
    const id = `${celula.fase}|${chave}`;
    if (!novos.has(id)) novos.set(id, `${celula.tipo.trim() || 'Não informado'} (${celula.fase})`);
  }
  return [...novos.values()];
}
