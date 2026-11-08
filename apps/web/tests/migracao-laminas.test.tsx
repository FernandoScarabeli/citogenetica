import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, NetworkError } from '../src/api/errors';
import type { ProtocoloDTO } from '../src/api/contracts/contagem';
import type { Entry, Slide, State } from '../src/domain';
import { idEstavel, mesclarBackup, migrarLaminas, uuidDeterministico } from '../src/migracao-laminas';
import { load, save } from '../src/storage';

const V4 = /^[\da-f]{8}-[\da-f]{4}-4[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/;
const VERSAO = 'aaaaaaaa-0000-4000-8000-000000000001';
const INTERFASE = 'bbbbbbbb-0000-4000-8000-000000000001';
const PROFASE = 'bbbbbbbb-0000-4000-8000-000000000002';
const MICRO = 'cccccccc-0000-4000-8000-000000000001';

const PROTOCOLOS: ProtocoloDTO[] = [
  { id: 'p-mitose', codigo: 'mitose', nome: 'Mitose', versaoAtual: { id: VERSAO, numero: 1, fases: [
    { id: INTERFASE, nome: 'Interfase', ordem: 1, contaComoDivisao: false, tipos: [] },
    { id: PROFASE, nome: 'Prófase', ordem: 2, contaComoDivisao: true, tipos: [{ id: MICRO, nome: 'Micronúcleo' }] },
  ] } },
  { id: 'p-meiose', codigo: 'meiose', nome: 'Meiose', versaoAtual: { id: 'v-meiose', numero: 1, fases: [{ id: 'm1', nome: 'Paquíteno', ordem: 1, contaComoDivisao: true, tipos: [] }] } },
];

const entry = (id: string, phase: string, status: Entry['status'], type = '', createdAt = '2025-03-02T10:00:00.000Z'): Entry => ({ id, phase, status, type, createdAt });
const slide = (id: string, entries: Entry[], extra: Partial<Slide> = {}): Slide => ({
  id, name: `Lâmina ${id}`, protocol: 'mitose', species: 'Allium cepa', treatment: 'controle', notes: 'Nota', createdAt: '2025-03-01T10:00:00.000Z', entries, ...extra,
});

const api = {
  listarProtocolos: vi.fn(), listarTiposPersonalizados: vi.fn(), criarTipoPersonalizado: vi.fn(),
  criarLamina: vi.fn(), criarRegistro: vi.fn(), buscarResumo: vi.fn(),
};
const apiError = (status: number, message = 'Falha.') => new ApiError({ status, error: 'Erro', message, path: '/x' });
const run = (laminas: Slide[], extra: { laboratorioId?: string | null; onProgresso?: never } = {}) =>
  migrarLaminas({ api: api as never, usuarioId: 7, laboratorioId: extra.laboratorioId ?? null, laminas });
const seed = (slides: Slide[], customTypes: State['customTypes'] = {}) => save({ version: 1, slides, customTypes });
const ids = () => load().state.slides.map(item => item.id);

beforeEach(() => {
  localStorage.clear();
  vi.resetAllMocks();
  api.listarProtocolos.mockResolvedValue(PROTOCOLOS);
  api.listarTiposPersonalizados.mockResolvedValue([]);
  api.criarTipoPersonalizado.mockImplementation(async (r: { id: string }) => r);
  api.criarLamina.mockImplementation(async (r: { id: string }) => r);
  api.criarRegistro.mockImplementation(async (_l: string, r: { id: string }) => r);
  api.buscarResumo.mockImplementation(async (id: string) => ({ laminaId: id, total: api.criarRegistro.mock.calls.length }));
});

describe('identificadores estáveis', () => {
  it('deriva UUIDv4 canônico e estável', async () => {
    const a = await uuidDeterministico('lamina|7|abc');
    expect(a).toMatch(V4);
    expect(await uuidDeterministico('lamina|7|abc')).toBe(a);
    expect(await uuidDeterministico('lamina|8|abc')).not.toBe(a);
  });
  it('mantém o id local que já é UUIDv4 minúsculo e deriva nos demais casos', async () => {
    const local = 'dddddddd-1111-4222-8333-444444444444';
    expect(await idEstavel(local, 'x')).toBe(local);
    expect(await idEstavel(local.toUpperCase(), 'x')).toMatch(V4);
    expect(await idEstavel('1730000000-abc', 'x')).toBe(await uuidDeterministico('x'));
  });
});

describe('envio de uma lâmina', () => {
  it('envia a lâmina com todos os campos e a meta legada, sem laboratório', async () => {
    seed([slide('s1', []), slide('s2', [], { protocol: 'meiose', goal: 500 }), slide('s3', [], { goal: null })]);
    const result = await run(load().state.slides);
    expect(result.enviadas).toHaveLength(3);
    const [primeira, segunda, terceira] = api.criarLamina.mock.calls.map(call => call[0]);
    expect(primeira).toEqual({ id: expect.stringMatching(V4), protocoloVersaoId: VERSAO, nome: 'Lâmina s1', especie: 'Allium cepa', tratamento: 'controle', observacoes: 'Nota', meta: 1000 });
    expect(primeira).not.toHaveProperty('laboratorioId');
    expect(segunda).toMatchObject({ protocoloVersaoId: 'v-meiose', meta: 500 });
    expect(terceira.meta).toBeNull();
  });

  it('usa o laboratório atual quando há um', async () => {
    seed([slide('s1', [])]);
    await run(load().state.slides, { laboratorioId: 'lab-1' });
    expect(api.criarLamina.mock.calls[0][0].laboratorioId).toBe('lab-1');
  });

  it('envia as células na ordem, com data original e sem fuso', async () => {
    const entries = [
      entry('e1', 'Interfase', 'normal', '', '2025-03-02T10:00:00.000Z'),
      entry('e2', 'Prófase', 'anormal', 'Micronúcleo', '2025-03-02T10:01:00.000Z'),
      entry('e3', 'Prófase', 'normal', '', 'data inválida'),
    ];
    seed([slide('s1', entries)]);
    await run(load().state.slides);
    const sent = api.criarRegistro.mock.calls.map(call => call[1]);
    expect(sent.map(item => item.faseId)).toEqual([INTERFASE, PROFASE, PROFASE]);
    expect(sent[0]).toMatchObject({ status: 'NORMAL', quantidade: 1, horaAparelho: '2025-03-02T10:00:00.000Z' });
    expect(sent[0]).not.toHaveProperty('tipoIds');
    expect(sent[1]).toMatchObject({ status: 'ANORMAL', tipoIds: [MICRO], horaAparelho: '2025-03-02T10:01:00.000Z' });
    for (const item of sent) { expect(item).not.toHaveProperty('fusoAparelho'); expect(item.id).toMatch(V4); }
    expect(sent[2]).not.toHaveProperty('horaAparelho');
    expect(api.criarRegistro.mock.calls[0][0]).toBe(api.criarLamina.mock.calls[0][0].id);
  });

  it('reconhece o tipo fixo pelo nome sem acento nem caixa', async () => {
    seed([slide('s1', [entry('e1', 'Prófase', 'anormal', '  MICRONUCLEO ')])]);
    await run(load().state.slides);
    expect(api.criarRegistro.mock.calls[0][1].tipoIds).toEqual([MICRO]);
    expect(api.criarTipoPersonalizado).not.toHaveBeenCalled();
  });

  it('cria tipo personalizado uma só vez e usa "Não informado" para tipo vazio', async () => {
    seed([slide('s1', [
      entry('e1', 'Prófase', 'anormal', 'Fragmento raro'), entry('e2', 'Prófase', 'anormal', 'fragmento  raro'), entry('e3', 'Prófase', 'anormal', ' '),
    ])]);
    await run(load().state.slides, { laboratorioId: 'lab-1' });
    expect(api.criarTipoPersonalizado).toHaveBeenCalledTimes(2);
    const [raro, vazio] = api.criarTipoPersonalizado.mock.calls.map(call => call[0]);
    expect(raro).toEqual({ id: expect.stringMatching(V4), faseId: PROFASE, laboratorioId: 'lab-1', nome: 'Fragmento raro' });
    expect(vazio.nome).toBe('Não informado');
    const sent = api.criarRegistro.mock.calls.map(call => call[1].tipoIds);
    expect(sent).toEqual([[raro.id], [raro.id], [vazio.id]]);
  });

  it('em conflito de tipo, recarrega a lista e reaproveita o de mesmo nome', async () => {
    api.criarTipoPersonalizado.mockRejectedValue(apiError(409));
    api.listarTiposPersonalizados.mockResolvedValue([{ id: 'tipo-existente', faseId: PROFASE, laboratorioId: null, nome: 'fragmento RARO' }]);
    seed([slide('s1', [entry('e1', 'Prófase', 'anormal', 'Fragmento raro')])]);
    const result = await run(load().state.slides);
    expect(api.listarTiposPersonalizados).toHaveBeenCalledWith(VERSAO, null);
    expect(api.criarRegistro.mock.calls[0][1].tipoIds).toEqual(['tipo-existente']);
    expect(result.enviadas).toHaveLength(1);
  });

  it('repetir depois de falha parcial usa os mesmos ids', async () => {
    const entries = [entry('e1', 'Interfase', 'normal'), entry('e2', 'Prófase', 'normal'), entry('e3', 'Prófase', 'normal')];
    seed([slide('s1', entries)]);
    api.criarRegistro
      .mockImplementationOnce(async (_l: string, r: unknown) => r)
      .mockRejectedValueOnce(apiError(400, 'Dado inválido.'));
    const first = await run(load().state.slides);
    expect(first.falhas).toEqual([{ id: 's1', nome: 'Lâmina s1', motivo: 'Dado inválido.' }]);
    expect(ids()).toEqual(['s1']);
    const firstIds = api.criarRegistro.mock.calls.map(call => call[1].id);
    const firstSlideId = api.criarLamina.mock.calls[0][0].id;

    api.criarRegistro.mockClear();
    api.criarRegistro.mockImplementation(async (_l: string, r: { id: string }) => r);
    api.buscarResumo.mockResolvedValue({ laminaId: 'x', total: 3 });
    const second = await run(load().state.slides);
    expect(second.enviadas).toHaveLength(1);
    expect(api.criarLamina.mock.calls[1][0].id).toBe(firstSlideId);
    const secondIds = api.criarRegistro.mock.calls.map(call => call[1].id);
    expect(secondIds.slice(0, 2)).toEqual(firstIds);
    expect(new Set(secondIds).size).toBe(3);
  });
});

describe('confirmação e remoção', () => {
  it('total divergente mantém a lâmina no navegador', async () => {
    seed([slide('s1', [entry('e1', 'Interfase', 'normal')])]);
    api.buscarResumo.mockResolvedValue({ laminaId: 'x', total: 0 });
    const result = await run(load().state.slides);
    expect(result.enviadas).toHaveLength(0);
    expect(result.falhas[0].motivo).toContain('0 de 1');
    expect(ids()).toEqual(['s1']);
  });

  it('a lâmina só sai depois da confirmação e lâmina sem células também é enviada', async () => {
    seed([slide('s1', [])]);
    let idsDuranteResumo: string[] = [];
    api.buscarResumo.mockImplementation(async () => { idsDuranteResumo = ids(); return { total: 0 }; });
    const result = await run(load().state.slides);
    expect(idsDuranteResumo).toEqual(['s1']);
    expect(result.enviadas).toHaveLength(1);
    expect(ids()).toEqual([]);
    expect(api.criarRegistro).not.toHaveBeenCalled();
  });

  it('preserva o que outra aba gravou durante o envio', async () => {
    seed([slide('s1', [])]);
    api.buscarResumo.mockImplementation(async () => {
      const current = load().state;
      save({ ...current, slides: [...current.slides, slide('outra-aba', [])] });
      return { total: 0 };
    });
    await run(load().state.slides);
    expect(ids()).toEqual(['outra-aba']);
  });

  it('falha ao gravar vira aviso e a lâmina conta como enviada', async () => {
    seed([slide('s1', [])]);
    const original = Storage.prototype.setItem;
    api.buscarResumo.mockImplementation(async () => {
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('cheio'); });
      return { total: 0 };
    });
    const result = await run(load().state.slides);
    vi.mocked(Storage.prototype.setItem).mockRestore();
    expect(Storage.prototype.setItem).toBe(original);
    expect(result.enviadas).toHaveLength(1);
    expect(result.avisos[0]).toContain('não saiu deste navegador');
    expect(ids()).toEqual(['s1']);
  });

  it('erro de uma lâmina não impede a seguinte', async () => {
    seed([slide('s1', [entry('e1', 'Interfase', 'normal')]), slide('s2', [])]);
    api.criarLamina.mockRejectedValueOnce(apiError(400, 'Nome inválido.'));
    const result = await run(load().state.slides);
    expect(result.falhas.map(item => item.id)).toEqual(['s1']);
    expect(result.enviadas.map(item => item.id)).toEqual(['s2']);
    expect(ids()).toEqual(['s1']);
  });

  it('erro de rede interrompe tudo e preserva as lâminas', async () => {
    seed([slide('s1', []), slide('s2', []), slide('s3', [])]);
    api.criarLamina.mockImplementationOnce(async (r: unknown) => r).mockRejectedValueOnce(new NetworkError());
    const result = await run(load().state.slides);
    expect(result.enviadas.map(item => item.id)).toEqual(['s1']);
    expect(result.falhas.map(item => item.id)).toEqual(['s2']);
    expect(result.naoTentadas).toBe(1);
    expect(result.interrupcao).toContain('Sem conexão');
    expect(api.criarLamina).toHaveBeenCalledTimes(2);
    expect(ids()).toEqual(['s2', 's3']);
  });

  it('401 interrompe tudo', async () => {
    seed([slide('s1', []), slide('s2', [])]);
    api.criarLamina.mockRejectedValue(apiError(401, 'Não autenticado.'));
    const result = await run(load().state.slides);
    expect(result.naoTentadas).toBe(1);
    expect(result.interrupcao).toContain('sessão');
    expect(ids()).toEqual(['s1', 's2']);
  });

  it('resposta vazia do servidor é falha, não sucesso', async () => {
    seed([slide('s1', [])]);
    api.criarLamina.mockResolvedValue(undefined);
    const result = await run(load().state.slides);
    expect(result.falhas).toHaveLength(1);
    expect(ids()).toEqual(['s1']);
  });
});

describe('mesclarBackup', () => {
  it('não sobrescreve lâmina já presente e soma tipos personalizados', () => {
    const atual: State = { version: 1, slides: [slide('a', [], { name: 'Atual' })], customTypes: { 'mitose:Prófase': ['X'] } };
    const backup: State = { version: 1, slides: [slide('a', [], { name: 'Antiga' }), slide('b', [])], customTypes: { 'mitose:Prófase': ['X', 'Y'] } };
    const { state, adicionadas } = mesclarBackup(atual, backup);
    expect(adicionadas).toBe(1);
    expect(state.slides.map(item => `${item.id}:${item.name}`)).toEqual(['a:Atual', 'b:Lâmina b']);
    expect(state.customTypes['mitose:Prófase']).toEqual(['X', 'Y']);
  });
});
