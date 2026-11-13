import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { ApiError, NetworkError } from '../src/api/errors';
import type { CriarLaminaRequestDTO, CriarRegistroRequestDTO, CriarTipoPersonalizadoRequestDTO, LaminaDTO, ProtocoloDTO, TipoPersonalizadoDTO } from '../src/api/contracts/contagem';
import { normalizarNomeTipo } from '../src/contagem-utils';

export const lerFixture = (nome: string): string => readFileSync(join(process.cwd(), 'tests/fixtures/legado', nome), 'utf8');

const erro = (status: number, message = 'Erro.') => new ApiError({ status, error: 'Erro', message, path: '/api/v1' });

/** Protocolos como no seed V12 (nomes e ordem do sistema). */
const FASES: Record<string, [string, string[]][]> = {
  meiose: [
    ['Leptóteno/Zigóteno', []], ['Paquíteno', []], ['Diplóteno/Diacinese', ['Univalente', 'Trivalente', 'Tetravalente']],
    ['Metáfase I', ['Cromossomos não orientados']], ['Anáfase I', ['Cromossomos retardatários', 'Segregação de cromátides']],
    ['Telófase I', ['Micronúcleo', 'Fuso tripolar']], ['Prófase II', ['Micronúcleo', 'Núcleos assincrônicos']],
    ['Metáfase II', ['Cromossomos não orientados', 'Assincronia']], ['Anáfase II', ['Cromossomos retardatários', 'Assincronia']],
    ['Telófase II', ['Micronúcleo', 'Ponte']], ['Tétrade', ['Micronúcleo', 'Tríade', 'Díade']],
  ],
  mitose: [
    ['Interfase', ['Núcleos apoptóticos (condensados)']], ['Prófase', ['Stickiness', 'Cromossomos ou fragmentos desprendidos', 'Núcleo com brotações', 'Micronúcleo']],
    ['Metáfase', ['Cromossomos não orientados', 'Metáfase colchicínica', 'Stickiness']], ['Anáfase', ['Cromossomos atrasados', 'Fuso anormal', 'Stickiness', 'Ponte', 'Ponte com fragmento']],
    ['Telófase', ['Fuso anormal', 'Ponte', 'Ponte com fragmento', 'Micronúcleo: 1', 'Micronúcleo: 2', 'Micronúcleo: 3', 'Micronúcleo: mais de 3']],
  ],
};

const uuid = (a: number, b: number) => `${String(a).padStart(8, '0')}-0000-4000-8000-${String(b).padStart(12, '0')}`;
export const PROTOCOLOS_REAIS: ProtocoloDTO[] = Object.entries(FASES).map(([codigo, fases], p) => ({
  id: uuid(1, p), codigo, nome: codigo === 'mitose' ? 'Mitose' : 'Meiose',
  versaoAtual: {
    id: uuid(2, p), numero: 1,
    fases: fases.map(([nome, tipos], f) => ({ id: uuid(3 + p, f), nome, ordem: f + 1, contaComoDivisao: nome !== 'Interfase', tipos: tipos.map((tipo, t) => ({ id: uuid(10 + p * 10 + f, t), nome: tipo })) })),
  },
}));

type Registro = CriarRegistroRequestDTO;
type Guardada = { dto: LaminaDTO; pedido: CriarLaminaRequestDTO; excluida: boolean; registros: Map<string, Registro> };

/** API em memória que imita a real: idempotência por id e conteúdo, 409, 404 de lâmina excluída e tipos personalizados normalizados. */
export function criarApiFalsa(usuarioId = 7) {
  const laminas = new Map<string, Guardada>();
  const tipos: (TipoPersonalizadoDTO & { faseId: string })[] = [];
  const estado = { falhaRedeAposRegistros: null as number | null, chamadasRegistro: 0, sessaoExpirada: false };

  const mesmoPedido = (a: object, b: object) => JSON.stringify(a) === JSON.stringify(b);

  const api = {
    async listarProtocolos() { return PROTOCOLOS_REAIS; },
    async listarTiposPersonalizados(_versaoId: string, laboratorioId?: string | null) {
      return tipos.filter(tipo => tipo.laboratorioId === (laboratorioId ?? null));
    },
    async criarTipoPersonalizado(pedido: CriarTipoPersonalizadoRequestDTO) {
      const laboratorioId = pedido.laboratorioId ?? null;
      const igual = tipos.find(tipo => tipo.id === pedido.id);
      const chave = normalizarNomeTipo(pedido.nome);
      if (igual) return igual;
      const fixos = PROTOCOLOS_REAIS.flatMap(p => p.versaoAtual.fases).find(fase => fase.id === pedido.faseId)?.tipos ?? [];
      if (fixos.some(tipo => normalizarNomeTipo(tipo.nome) === chave) || tipos.some(tipo => tipo.faseId === pedido.faseId && tipo.laboratorioId === laboratorioId && normalizarNomeTipo(tipo.nome) === chave)) throw erro(409, 'Já existe um tipo com este nome.');
      const novo = { id: pedido.id, faseId: pedido.faseId, laboratorioId, nome: pedido.nome };
      tipos.push(novo);
      return novo;
    },
    async criarLamina(pedido: CriarLaminaRequestDTO): Promise<LaminaDTO> {
      if (estado.sessaoExpirada) throw erro(401, 'Sessão expirada.');
      const existente = laminas.get(pedido.id);
      const { criadaEm: _criadaEm, ...conteudo } = pedido;
      if (existente) {
        const { criadaEm: _outra, ...conteudoExistente } = existente.pedido;
        if (!mesmoPedido(conteudo, conteudoExistente)) throw erro(409, 'Id já usado com outro conteúdo.');
        return { ...existente.dto, totalCelulas: existente.registros.size };
      }
      const dto: LaminaDTO = {
        id: pedido.id, versao: 0, donoUsuarioId: usuarioId, donoNome: 'Ana', laboratorioId: pedido.laboratorioId ?? null, protocoloVersaoId: pedido.protocoloVersaoId,
        nome: pedido.nome, especie: pedido.especie ?? '', tratamento: pedido.tratamento ?? '', observacoes: pedido.observacoes ?? '', meta: pedido.meta ?? null,
        totalCelulas: 0, criadaEm: pedido.criadaEm ?? '2026-11-12T00:00:00Z', atualizadaEm: '2026-11-12T00:00:00Z',
      };
      laminas.set(pedido.id, { dto, pedido, excluida: false, registros: new Map() });
      return dto;
    },
    async buscarLamina(id: string): Promise<LaminaDTO> {
      const lamina = laminas.get(id);
      if (!lamina || lamina.excluida) throw erro(404, 'Lâmina não encontrada.');
      return { ...lamina.dto, totalCelulas: lamina.registros.size };
    },
    async criarRegistro(laminaId: string, pedido: Registro) {
      estado.chamadasRegistro += 1;
      if (estado.falhaRedeAposRegistros !== null && estado.chamadasRegistro > estado.falhaRedeAposRegistros) throw new NetworkError();
      const lamina = laminas.get(laminaId);
      if (!lamina || lamina.excluida) throw erro(404, 'Lâmina não encontrada.');
      const existente = lamina.registros.get(pedido.id);
      if (existente) {
        if (!mesmoPedido(existente, pedido)) throw erro(409, 'Id já usado com outro pedido.');
        return existente;
      }
      lamina.registros.set(pedido.id, pedido);
      return pedido;
    },
    async buscarResumo(laminaId: string) {
      const lamina = laminas.get(laminaId);
      if (!lamina || lamina.excluida) throw erro(404, 'Lâmina não encontrada.');
      return { laminaId, total: lamina.registros.size };
    },
    async listarLaminas() { return { content: [...laminas.values()].filter(item => !item.excluida).map(item => ({ ...item.dto, totalCelulas: item.registros.size })), currentPage: 0, totalPages: 1, totalElements: laminas.size }; },
  };

  return {
    api, estado, laminas, tipos,
    excluir(id: string) { const lamina = laminas.get(id); if (lamina) lamina.excluida = true; },
    registrosDe: (id: string) => [...(laminas.get(id)?.registros.values() ?? [])],
    total: () => [...laminas.values()].reduce((soma, item) => soma + item.registros.size, 0),
  };
}
