import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { ApiError, NetworkError } from '../src/api/errors';
import type {
  LaminaDTO, ProtocoloDTO, RegistroHistoricoDTO, ResumoLaminaDTO, TipoPersonalizadoDTO,
} from '../src/api/contracts/contagem';
import type { LaboratorioResponseDTO } from '../src/api/contracts/laboratorio';
import { Slides } from '../src/Slides';
import { salvarArquivo } from '../src/salvar-arquivo';

vi.mock('../src/salvar-arquivo', () => ({ salvarArquivo: vi.fn() }));

const VERSAO = 'aaaaaaaa-0000-4000-8000-000000000001';
const INTERFASE = 'bbbbbbbb-0000-4000-8000-000000000001';
const PROFASE = 'bbbbbbbb-0000-4000-8000-000000000002';
const MICRO = 'cccccccc-0000-4000-8000-000000000001';
const STICK = 'cccccccc-0000-4000-8000-000000000002';

const PROTOCOLOS: ProtocoloDTO[] = [
  {
    id: 'p-meiose', codigo: 'meiose', nome: 'Meiose',
    versaoAtual: { id: 'v-meiose', numero: 1, fases: [{ id: 'm1', nome: 'Paquíteno', ordem: 1, contaComoDivisao: true, tipos: [] }] },
  },
  {
    id: 'p-mitose', codigo: 'mitose', nome: 'Mitose',
    versaoAtual: {
      id: VERSAO, numero: 1, fases: [
        { id: INTERFASE, nome: 'Interfase', ordem: 1, contaComoDivisao: false, tipos: [] },
        { id: PROFASE, nome: 'Prófase', ordem: 2, contaComoDivisao: true, tipos: [{ id: MICRO, nome: 'Micronúcleo' }, { id: STICK, nome: 'Stickiness' }] },
      ],
    },
  },
];

const ME = 12;
const LAMINA: LaminaDTO = {
  id: 'lamina-1', versao: 3, donoUsuarioId: ME, donoNome: 'Ana Pesquisadora', laboratorioId: null, protocoloVersaoId: VERSAO,
  nome: 'L1 planta A', especie: 'Allium cepa', tratamento: 'controle', observacoes: 'Nota salva', meta: 1000, totalCelulas: 10,
  criadaEm: '2026-11-03T12:00:00Z', atualizadaEm: '2026-11-03T12:00:00Z',
};
const RESUMO: ResumoLaminaDTO = {
  laminaId: 'lamina-1', total: 3, anormais: 1, indiceMitotico: 66.666666, fases: [
    { faseId: INTERFASE, nome: 'Interfase', ordem: 1, normais: 1, anormais: 0, total: 1, percentualAnormais: 0, tipos: [] },
    { faseId: PROFASE, nome: 'Prófase', ordem: 2, normais: 1, anormais: 1, total: 2, percentualAnormais: 50, tipos: [{ tipoId: MICRO, nome: 'Micronúcleo', contagem: 1, percentual: 50 }] },
  ],
};
const REGISTRO: RegistroHistoricoDTO = {
  id: 'reg-2', versao: 2, laminaId: 'lamina-1', faseId: PROFASE, status: 'ANORMAL', tipoIds: [MICRO, STICK], quantidade: 3,
  registradoEm: '2026-11-08T15:00:00Z', horaAparelho: null, fusoAparelho: null,
  tipos: [{ id: MICRO, nome: 'Micronúcleo' }, { id: STICK, nome: 'Stickiness' }],
};
const REGISTRO_ANTIGO: RegistroHistoricoDTO = {
  id: 'reg-1', versao: 1, laminaId: 'lamina-1', faseId: INTERFASE, status: 'NORMAL', tipoIds: [], quantidade: 1,
  registradoEm: '2026-11-08T14:00:00Z', horaAparelho: null, fusoAparelho: null, tipos: [],
};

const page = <T,>(content: T[], currentPage = 0, totalPages = 1) => ({ content, currentPage, totalPages, totalElements: content.length });
const apiError = (status: number, message = 'Falha do servidor.') => new ApiError({ status, error: 'Erro', message, path: '/api/v1/laminas' });

const api = {
  listarProtocolos: vi.fn(),
  buscarVersaoProtocolo: vi.fn(),
  listarTiposPersonalizados: vi.fn(),
  criarTipoPersonalizado: vi.fn(),
  listarLaminas: vi.fn(),
  buscarLamina: vi.fn(),
  criarLamina: vi.fn(),
  atualizarLamina: vi.fn(),
  excluirLamina: vi.fn(),
  criarRegistro: vi.fn(),
  listarRegistros: vi.fn(),
  atualizarRegistro: vi.fn(),
  desfazerRegistros: vi.fn(),
  buscarResumo: vi.fn(),
  baixarLaminaJson: vi.fn(),
  baixarLaminaResumoCsv: vi.fn(),
  baixarLaminaRegistrosCsv: vi.fn(),
  baixarLaminaRelatorioPdf: vi.fn(),
  baixarLaminasJson: vi.fn(),
  baixarLaminasResumoCsv: vi.fn(),
  baixarLaminasRegistrosCsv: vi.fn(),
};
const ARQUIVO = { blob: new Blob(['x']), nome: 'lamina-1.csv' };

const ACTIVE_LAB: LaboratorioResponseDTO = { id: 'lab-1', nome: 'Citogenética Vegetal', estado: 'ATIVO', papeis: ['MEMBRO'], versao: 1 };

function renderSlides(props: { laboratory?: LaboratorioResponseDTO | null; readOnly?: boolean; usuarioId?: number } = {}) {
  return render(<MemoryRouter><Slides laboratory={props.laboratory ?? null} onBack={vi.fn()} readOnly={props.readOnly ?? false} usuarioId={props.usuarioId ?? ME} api={api as never} /></MemoryRouter>);
}

async function openSlide(user: ReturnType<typeof userEvent.setup>, name = /L1 planta A/) {
  await user.click(await screen.findByRole('button', { name }));
  await screen.findByRole('heading', { name: 'L1 planta A' });
}

const normalButton = () => screen.getByRole('button', { name: /Registrar Prófase normal/ });
const abnormalButton = () => screen.getByRole('button', { name: /Registrar Prófase anormal/ });
/** Número mostrado no cartão da fase (o botão de contar não mostra mais o número). */
const contagemProfase = (status: 'normal' | 'anormal' = 'normal') =>
  screen.getByRole('article', { name: 'Prófase' }).querySelector(`.phase-count.is-${status === 'normal' ? 'normal' : 'abnormal'} strong`)!.textContent;
const abrirHistorico = (user: ReturnType<typeof userEvent.setup>) => user.click(screen.getByText('Histórico de registros'));
const historico = () => within(screen.getByRole('group', { name: 'Histórico de registros' }));

beforeEach(() => {
  localStorage.clear();
  vi.resetAllMocks();
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value(this: HTMLDialogElement) { this.setAttribute('open', ''); } });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value(this: HTMLDialogElement) { this.removeAttribute('open'); } });
  api.listarProtocolos.mockResolvedValue(PROTOCOLOS);
  api.listarLaminas.mockResolvedValue(page([LAMINA]));
  api.buscarLamina.mockResolvedValue(LAMINA);
  api.buscarVersaoProtocolo.mockResolvedValue(PROTOCOLOS[1].versaoAtual);
  api.buscarResumo.mockResolvedValue(RESUMO);
  api.listarRegistros.mockResolvedValue(page([REGISTRO, REGISTRO_ANTIGO]));
  api.listarTiposPersonalizados.mockResolvedValue([]);
  api.criarRegistro.mockResolvedValue({});
  api.desfazerRegistros.mockResolvedValue({ id: 'x', quantidadeDesfeita: 1, registros: [] });
});

describe('lista de lâminas', () => {
  it('carrega todas as páginas em sequência e a busca vale para todas', async () => {
    const outra = { ...LAMINA, id: 'lamina-2', nome: 'Raiz de cebola' };
    api.listarLaminas
      .mockResolvedValueOnce(page([LAMINA], 0, 2))
      .mockResolvedValueOnce(page([outra], 1, 2));
    const user = userEvent.setup();
    renderSlides();
    await screen.findByRole('button', { name: /Raiz de cebola/ });

    expect(api.listarLaminas).toHaveBeenCalledTimes(2);
    expect(api.listarLaminas).toHaveBeenNthCalledWith(1, expect.objectContaining({ page: 0, size: 100, signal: expect.any(AbortSignal) }));
    expect(api.listarLaminas).toHaveBeenNthCalledWith(2, expect.objectContaining({ page: 1, size: 100 }));
    expect(api.listarLaminas.mock.calls[0][0]).not.toHaveProperty('laboratorioId');

    await user.type(screen.getByPlaceholderText('Buscar lâmina'), 'cebola');
    expect(screen.queryByRole('button', { name: /L1 planta A/ })).toBeNull();
    expect(screen.getByRole('button', { name: /Raiz de cebola/ })).toBeTruthy();
  });

  it('mostra dono, total de células e data da API e usa o laboratório na consulta', async () => {
    renderSlides({ laboratory: ACTIVE_LAB });
    const row = await screen.findByRole('button', { name: /L1 planta A/ });
    expect(api.listarLaminas).toHaveBeenCalledWith(expect.objectContaining({ laboratorioId: 'lab-1' }));
    expect(row.textContent).toContain('Ana Pesquisadora');
    expect(row.textContent).toContain('10');
    expect(row.textContent).toContain('nov');
  });

  it('permite tentar de novo quando a carga falha', async () => {
    api.listarLaminas.mockRejectedValueOnce(new NetworkError()).mockResolvedValue(page([LAMINA]));
    const user = userEvent.setup();
    renderSlides();
    await user.click(await screen.findByRole('button', { name: 'Tentar de novo' }));
    expect(await screen.findByRole('button', { name: /L1 planta A/ })).toBeTruthy();
  });

  it('cria lâmina com o protocolo escolhido e a meta padrão', async () => {
    api.listarLaminas.mockResolvedValue(page([]));
    api.criarLamina.mockResolvedValue({ ...LAMINA, id: 'nova', nome: 'L9' });
    api.buscarLamina.mockResolvedValue({ ...LAMINA, id: 'nova', nome: 'L9' });
    const user = userEvent.setup();
    renderSlides({ laboratory: ACTIVE_LAB });
    await user.click((await screen.findAllByRole('button', { name: 'Nova lâmina' }))[0]);
    await user.type(screen.getByLabelText('Identificação da lâmina'), 'L9');
    expect((screen.getByLabelText(/Meta de contagem/) as HTMLInputElement).value).toBe('1000');
    await user.click(screen.getByRole('button', { name: /Criar lâmina/ }));

    await waitFor(() => expect(api.criarLamina).toHaveBeenCalledTimes(1));
    expect(api.criarLamina).toHaveBeenCalledWith({
      id: expect.stringMatching(/^[0-9a-f-]{36}$/), protocoloVersaoId: VERSAO, laboratorioId: 'lab-1', nome: 'L9', especie: '', tratamento: '', meta: 1000,
    });
    await screen.findByRole('heading', { name: 'L9' });
  });

  it('mostra a mensagem quando o servidor nega a criação', async () => {
    api.criarLamina.mockRejectedValue(apiError(403, 'Acesso negado.'));
    const user = userEvent.setup();
    renderSlides();
    await user.click(await screen.findByRole('button', { name: 'Nova lâmina' }));
    await user.type(screen.getByLabelText('Identificação da lâmina'), 'L9');
    await user.click(screen.getByRole('button', { name: /Criar lâmina/ }));
    expect((await screen.findByRole('alert')).textContent).toContain('permissão');
  });

  it('em laboratório desativado ninguém cria nem altera, nem o dono', async () => {
    const user = userEvent.setup();
    const { container } = renderSlides({ laboratory: { ...ACTIVE_LAB, estado: 'DESATIVADO' }, readOnly: true });
    await screen.findByRole('button', { name: /L1 planta A/ });
    expect(screen.queryByRole('button', { name: 'Nova lâmina' })).toBeNull();
    expect(screen.getByText(/laboratório está desativado/)).toBeTruthy();

    await openSlide(user);
    expect(screen.queryByRole('button', { name: /Registrar Prófase/ })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Editar lâmina' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Excluir lâmina' })).toBeNull();
    expect(screen.queryByRole('button', { name: /Desfazer último/ })).toBeNull();
    expect((container.querySelector('textarea') as HTMLTextAreaElement).readOnly).toBe(true);
    expect(screen.queryByRole('button', { name: /Importar|Backup/ })).toBeNull();
    expect(api.listarTiposPersonalizados).not.toHaveBeenCalled();
  });
});

describe('quem edita', () => {
  it('o dono vê os controles e carrega os tipos personalizados', async () => {
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    expect(normalButton()).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Editar lâmina' })).toBeTruthy();
    expect(api.listarTiposPersonalizados).toHaveBeenCalledWith(VERSAO, null, expect.any(AbortSignal));
  });

  it('o orientador abre a lâmina em somente leitura, com aviso de quem é', async () => {
    api.listarLaminas.mockResolvedValue(page([{ ...LAMINA, donoUsuarioId: 99, donoNome: 'Bruno Aluno' }]));
    api.buscarLamina.mockResolvedValue({ ...LAMINA, donoUsuarioId: 99, donoNome: 'Bruno Aluno' });
    const user = userEvent.setup();
    const { container } = renderSlides({ laboratory: { ...ACTIVE_LAB, papeis: ['MEMBRO', 'ORIENTADOR'] } });
    await openSlide(user);

    expect(screen.getByText(/Esta lâmina é de Bruno Aluno/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Registrar Prófase/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Ajustar quantidade/ })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Editar lâmina' })).toBeNull();
    expect(screen.queryByRole('button', { name: /Desfazer último/ })).toBeNull();
    expect((container.querySelector('textarea') as HTMLTextAreaElement).readOnly).toBe(true);
    expect(api.listarTiposPersonalizados).not.toHaveBeenCalled();
    expect(contagemProfase('normal')).toBe('1');
    expect(contagemProfase('anormal')).toBe('1');
  });

  it('mostra tentar de novo se o detalhe falhar', async () => {
    api.buscarResumo.mockRejectedValueOnce(new NetworkError());
    const user = userEvent.setup();
    renderSlides();
    await user.click(await screen.findByRole('button', { name: /L1 planta A/ }));
    await user.click(await screen.findByRole('button', { name: 'Tentar de novo' }));
    await screen.findByRole('heading', { name: 'L1 planta A' });
  });

  it('volta à lista com aviso se a lâmina não existir mais', async () => {
    api.buscarLamina.mockRejectedValue(apiError(404));
    const user = userEvent.setup();
    renderSlides();
    await user.click(await screen.findByRole('button', { name: /L1 planta A/ }));
    expect((await screen.findByRole('status')).textContent).toContain('não está mais disponível');
    expect(screen.queryByRole('button', { name: /L1 planta A/ })).toBeNull();
  });
});

describe('contagem rápida', () => {
  it('sobe o contador na hora e envia o registro com hora e fuso do aparelho', async () => {
    let resolve!: (value: unknown) => void;
    api.criarRegistro.mockReturnValue(new Promise(done => { resolve = done; }));
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    expect(contagemProfase()).toBe('1');

    await user.click(normalButton());
    expect(contagemProfase()).toBe('2');
    expect(api.criarRegistro).toHaveBeenCalledTimes(1);
    expect(api.criarRegistro).toHaveBeenCalledWith('lamina-1', expect.objectContaining({
      id: expect.stringMatching(/^[0-9a-f-]{36}$/), faseId: PROFASE, status: 'NORMAL', quantidade: 1,
      horaAparelho: expect.stringMatching(/Z$/), fusoAparelho: expect.any(String),
    }));
    expect(api.criarRegistro.mock.calls[0][1]).not.toHaveProperty('tipoIds');

    api.buscarResumo.mockResolvedValue({ ...RESUMO, total: 4, fases: [RESUMO.fases[0], { ...RESUMO.fases[1], normais: 2, total: 3 }] });
    resolve({});
    await waitFor(() => expect(api.buscarResumo).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(contagemProfase()).toBe('2'));
  });

  it('vários cliques rápidos viram envios em fila e um único recarregamento', async () => {
    const resolvers: ((value: unknown) => void)[] = [];
    api.criarRegistro.mockImplementation(() => new Promise(done => { resolvers.push(done); }));
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);

    await user.click(normalButton());
    await user.click(normalButton());
    await user.click(normalButton());
    expect(contagemProfase()).toBe('4');
    expect(api.criarRegistro).toHaveBeenCalledTimes(1);

    resolvers[0]({});
    await waitFor(() => expect(api.criarRegistro).toHaveBeenCalledTimes(2));
    resolvers[1]({});
    await waitFor(() => expect(api.criarRegistro).toHaveBeenCalledTimes(3));
    expect(api.buscarResumo).toHaveBeenCalledTimes(1);
    expect(new Set(api.criarRegistro.mock.calls.map(call => call[1].id)).size).toBe(3);

    api.buscarResumo.mockResolvedValue({ ...RESUMO, total: 6, fases: [RESUMO.fases[0], { ...RESUMO.fases[1], normais: 4, total: 5 }] });
    resolvers[2]({});
    await waitFor(() => expect(api.buscarResumo).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(contagemProfase()).toBe('4'));
    expect(api.buscarLamina).toHaveBeenCalledTimes(2);
  });

  it('não grava nada no armazenamento do navegador', async () => {
    const get = vi.spyOn(Storage.prototype, 'getItem');
    const set = vi.spyOn(Storage.prototype, 'setItem');
    const remove = vi.spyOn(Storage.prototype, 'removeItem');
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    // A lista lê as lâminas antigas para oferecer o envio; contar não pode tocar no armazenamento.
    get.mockClear();
    await user.click(normalButton());
    await waitFor(() => expect(api.buscarResumo).toHaveBeenCalledTimes(2));
    for (const spy of [get, set, remove]) {
      expect(spy.mock.calls.filter(call => call[0] === 'citogenetica:workspace:v1')).toEqual([]);
    }
    expect(set).not.toHaveBeenCalled();
    expect(localStorage.length).toBe(0);
  });
});

describe('anormalidades', () => {
  it('registra uma célula com dois tipos', async () => {
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(abnormalButton());
    await user.click(screen.getByRole('button', { name: 'Marcar várias' }));
    await user.click(screen.getByRole('checkbox', { name: 'Micronúcleo' }));
    await user.click(screen.getByRole('checkbox', { name: 'Stickiness' }));
    await user.click(screen.getByRole('button', { name: /Registrar célula/ }));

    await waitFor(() => expect(api.criarRegistro).toHaveBeenCalledTimes(1));
    expect(api.criarRegistro).toHaveBeenCalledWith('lamina-1', expect.objectContaining({ faseId: PROFASE, status: 'ANORMAL', tipoIds: [MICRO, STICK], quantidade: 1 }));
  });

  it('um toque no tipo registra a célula e fecha a janela', async () => {
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(abnormalButton());
    expect(screen.getByRole('heading', { name: 'Célula anormal · Prófase' })).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Stickiness' }));

    await waitFor(() => expect(api.criarRegistro).toHaveBeenCalledTimes(1));
    expect(api.criarRegistro).toHaveBeenCalledWith('lamina-1', expect.objectContaining({ faseId: PROFASE, status: 'ANORMAL', tipoIds: [STICK], quantidade: 1 }));
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('"Outro" no modo de um toque abre o campo e registra com Registrar célula', async () => {
    const criado: TipoPersonalizadoDTO = { id: 'dddddddd-0000-4000-8000-000000000002', faseId: PROFASE, laboratorioId: null, nome: 'Fragmento' };
    api.criarTipoPersonalizado.mockResolvedValue(criado);
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(abnormalButton());
    await user.click(screen.getByRole('button', { name: 'Outro…' }));
    await user.type(screen.getByLabelText('Descreva a anormalidade'), 'Fragmento');
    await user.click(screen.getByRole('button', { name: /Registrar célula/ }));

    await waitFor(() => expect(api.criarRegistro).toHaveBeenCalledTimes(1));
    expect(api.criarRegistro.mock.calls[0][1].tipoIds).toEqual([criado.id]);
  });

  it('o toque único sem conexão vai para a fila', async () => {
    api.criarRegistro.mockRejectedValue(new NetworkError());
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(abnormalButton());
    await user.click(screen.getByRole('button', { name: 'Micronúcleo' }));
    expect(await screen.findByText(/Sem internet\. 1 célula guardada/)).toBeTruthy();
    expect(JSON.parse(localStorage.getItem('citogenetica:fila-registros:v1') ?? '[]')[0].pedido).toEqual(expect.objectContaining({ status: 'ANORMAL', tipoIds: [MICRO] }));
  });

  it('exige ao menos um tipo, com erro acessível', async () => {
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(abnormalButton());
    await user.click(screen.getByRole('button', { name: 'Marcar várias' }));
    await user.click(screen.getByRole('button', { name: /Registrar célula/ }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('ao menos uma anormalidade');
    expect(screen.getByRole('group', { name: /Anormalidades em Prófase/ }).getAttribute('aria-describedby')).toBe(alert.id);
    expect(api.criarRegistro).not.toHaveBeenCalled();
  });

  it('"Outro" cria a anormalidade personalizada e registra com ela', async () => {
    const criado: TipoPersonalizadoDTO = { id: 'dddddddd-0000-4000-8000-000000000001', faseId: PROFASE, laboratorioId: null, nome: 'Ponte dupla' };
    api.criarTipoPersonalizado.mockResolvedValue(criado);
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(abnormalButton());
    await user.click(screen.getByRole('button', { name: 'Marcar várias' }));
    await user.click(screen.getByRole('checkbox', { name: 'Outro (especificar)' }));
    await user.type(screen.getByLabelText('Descreva a anormalidade'), '  Ponte   dupla ');
    await user.click(screen.getByRole('button', { name: /Registrar célula/ }));

    await waitFor(() => expect(api.criarRegistro).toHaveBeenCalledTimes(1));
    expect(api.criarTipoPersonalizado).toHaveBeenCalledWith({ id: expect.any(String), faseId: PROFASE, laboratorioId: null, nome: 'Ponte dupla' });
    expect(api.criarRegistro.mock.calls[0][1].tipoIds).toEqual([criado.id]);
  });

  it('"Outro" sem acento usa o tipo fixo existente, sem criar tipo', async () => {
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(abnormalButton());
    await user.click(screen.getByRole('button', { name: 'Marcar várias' }));
    await user.click(screen.getByRole('checkbox', { name: 'Outro (especificar)' }));
    await user.type(screen.getByLabelText('Descreva a anormalidade'), 'micronucleo');
    await user.click(screen.getByRole('button', { name: /Registrar célula/ }));

    await waitFor(() => expect(api.criarRegistro).toHaveBeenCalledTimes(1));
    expect(api.criarTipoPersonalizado).not.toHaveBeenCalled();
    expect(api.criarRegistro.mock.calls[0][1].tipoIds).toEqual([MICRO]);
  });

  it('em 409 encontra o personalizado acentuado que só apareceu na lista recarregada', async () => {
    const existente: TipoPersonalizadoDTO = { id: 'dddddddd-0000-4000-8000-000000000003', faseId: PROFASE, laboratorioId: null, nome: 'Cromossomo ânulo' };
    api.criarTipoPersonalizado.mockRejectedValue(apiError(409));
    api.listarTiposPersonalizados.mockResolvedValueOnce([]).mockResolvedValueOnce([existente]);
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(abnormalButton());
    await user.click(screen.getByRole('button', { name: 'Marcar várias' }));
    await user.click(screen.getByRole('checkbox', { name: 'Outro (especificar)' }));
    await user.type(screen.getByLabelText('Descreva a anormalidade'), 'cromossomo anulo');
    await user.click(screen.getByRole('button', { name: /Registrar célula/ }));

    await waitFor(() => expect(api.criarRegistro).toHaveBeenCalledTimes(1));
    expect(api.criarRegistro.mock.calls[0][1].tipoIds).toEqual([existente.id]);
  });

  it('em 409 recarrega os tipos e usa o existente pelo nome normalizado', async () => {
    const existente: TipoPersonalizadoDTO = { id: 'dddddddd-0000-4000-8000-000000000002', faseId: PROFASE, laboratorioId: null, nome: 'Ponte Dupla' };
    api.criarTipoPersonalizado.mockRejectedValue(apiError(409));
    api.listarTiposPersonalizados.mockResolvedValueOnce([]).mockResolvedValueOnce([existente]);
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(abnormalButton());
    await user.click(screen.getByRole('button', { name: 'Marcar várias' }));
    await user.click(screen.getByRole('checkbox', { name: 'Outro (especificar)' }));
    await user.type(screen.getByLabelText('Descreva a anormalidade'), 'ponte dupla');
    await user.click(screen.getByRole('button', { name: /Registrar célula/ }));

    await waitFor(() => expect(api.criarRegistro).toHaveBeenCalledTimes(1));
    expect(api.listarTiposPersonalizados).toHaveBeenCalledTimes(2);
    expect(api.criarRegistro.mock.calls[0][1].tipoIds).toEqual([existente.id]);
  });
});

describe('ajuste em lote', () => {
  async function openBatch(user: ReturnType<typeof userEvent.setup>) {
    renderSlides();
    await openSlide(user);
    await user.click(screen.getByRole('button', { name: /Ajustar quantidade\s*em Prófase/ }));
  }

  it('adiciona células em um único registro com a quantidade', async () => {
    const user = userEvent.setup();
    await openBatch(user);
    await user.type(screen.getByLabelText('Quantidade de células'), '25');
    await user.click(screen.getByRole('button', { name: 'Adicionar células' }));

    await waitFor(() => expect(api.criarRegistro).toHaveBeenCalledTimes(1));
    expect(api.criarRegistro).toHaveBeenCalledWith('lamina-1', expect.objectContaining({ faseId: PROFASE, status: 'NORMAL', quantidade: 25 }));
    await waitFor(() => expect(api.buscarResumo).toHaveBeenCalledTimes(2));
  });

  it('mostra a mensagem da API quando passa do limite', async () => {
    api.criarRegistro.mockRejectedValue(apiError(400, 'A quantidade deve ser de no máximo 1000.'));
    const user = userEvent.setup();
    await openBatch(user);
    await user.type(screen.getByLabelText('Quantidade de células'), '5000');
    await user.click(screen.getByRole('button', { name: 'Adicionar células' }));
    expect((await screen.findByRole('alert')).textContent).toBe('A quantidade deve ser de no máximo 1000.');
  });

  const REGISTRO_SO_MICRO: RegistroHistoricoDTO = { ...REGISTRO, id: 'reg-3', tipoIds: [MICRO], quantidade: 4, tipos: [{ id: MICRO, nome: 'Micronúcleo' }] };

  async function openRemoveAbnormal(user: ReturnType<typeof userEvent.setup>) {
    api.listarRegistros.mockResolvedValue(page([REGISTRO, REGISTRO_SO_MICRO, REGISTRO_ANTIGO]));
    await openBatch(user);
    await user.click(screen.getByRole('button', { name: 'Remover' }));
    await user.click(screen.getByRole('button', { name: 'Anormal' }));
  }

  it('remover anormais lista as combinações da fase com a quantidade de cada uma', async () => {
    const user = userEvent.setup();
    await openRemoveAbnormal(user);
    expect(await screen.findByRole('radio', { name: 'Micronúcleo (4)' })).toBeTruthy();
    expect(screen.getByRole('radio', { name: 'Micronúcleo + Stickiness (3)' })).toBeTruthy();
    expect(screen.queryByRole('checkbox', { name: 'Stickiness' })).toBeNull();
  });

  it('remove a combinação escolhida com desfazer', async () => {
    const user = userEvent.setup();
    await openRemoveAbnormal(user);
    await user.click(await screen.findByRole('radio', { name: 'Micronúcleo + Stickiness (3)' }));
    await user.type(screen.getByLabelText('Quantidade de células'), '2');
    await user.click(screen.getByRole('button', { name: 'Remover células' }));

    await waitFor(() => expect(api.desfazerRegistros).toHaveBeenCalledTimes(1));
    expect(api.desfazerRegistros).toHaveBeenCalledWith('lamina-1', { id: expect.any(String), faseId: PROFASE, status: 'ANORMAL', tipoIds: [MICRO, STICK], quantidade: 2 });
  });

  it('não deixa remover mais do que há na combinação', async () => {
    const user = userEvent.setup();
    await openRemoveAbnormal(user);
    await user.click(await screen.findByRole('radio', { name: 'Micronúcleo (4)' }));
    await user.type(screen.getByLabelText('Quantidade de células'), '5');
    await user.click(screen.getByRole('button', { name: 'Remover células' }));

    expect((await screen.findByRole('alert')).textContent).toContain('Só há 4 células');
    expect(api.desfazerRegistros).not.toHaveBeenCalled();
  });

  it('exige escolher a anormalidade a remover', async () => {
    const user = userEvent.setup();
    await openRemoveAbnormal(user);
    await screen.findByRole('radio', { name: 'Micronúcleo (4)' });
    await user.type(screen.getByLabelText('Quantidade de células'), '1');
    await user.click(screen.getByRole('button', { name: 'Remover células' }));
    expect((await screen.findByRole('alert')).textContent).toContain('Escolha qual anormalidade');
    expect(api.desfazerRegistros).not.toHaveBeenCalled();
  });

  it('mostra a mensagem da API em 409 ao remover', async () => {
    api.desfazerRegistros.mockRejectedValue(apiError(409, 'Não há células suficientes para remover.'));
    const user = userEvent.setup();
    await openBatch(user);
    await user.click(screen.getByRole('button', { name: 'Remover' }));
    await user.type(screen.getByLabelText('Quantidade de células'), '9');
    await user.click(screen.getByRole('button', { name: 'Remover células' }));
    expect((await screen.findByRole('alert')).textContent).toBe('Não há células suficientes para remover.');
  });
});

describe('desfazer último', () => {
  it('diz o que vai desfazer e, com mais de uma célula, pede confirmação antes', async () => {
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(screen.getByRole('button', { name: 'Desfazer: 3 anormais em Prófase' }));

    expect(api.desfazerRegistros).not.toHaveBeenCalled();
    const dialog = screen.getByRole('dialog');
    expect(dialog.textContent).toContain('3 células anormais de Prófase');
    await user.click(within(dialog).getByRole('button', { name: /Desfazer/ }));

    await waitFor(() => expect(api.desfazerRegistros).toHaveBeenCalledTimes(1));
    expect(api.desfazerRegistros).toHaveBeenCalledWith('lamina-1', { id: expect.any(String), faseId: PROFASE, status: 'ANORMAL', tipoIds: [MICRO, STICK], quantidade: 3 });
    await waitFor(() => expect(api.buscarResumo).toHaveBeenCalledTimes(2));
  });

  it('cancelar a confirmação não desfaz nada', async () => {
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(screen.getByRole('button', { name: /Desfazer: 3 anormais/ }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }));
    expect(api.desfazerRegistros).not.toHaveBeenCalled();
  });

  it('desfaz direto quando o último registro tem uma só célula', async () => {
    api.listarRegistros.mockResolvedValue(page([{ ...REGISTRO_ANTIGO, quantidade: 1 }]));
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(screen.getByRole('button', { name: 'Desfazer: 1 normal em Interfase' }));
    await waitFor(() => expect(api.desfazerRegistros).toHaveBeenCalledWith('lamina-1', expect.objectContaining({ faseId: INTERFASE, status: 'NORMAL', quantidade: 1 })));
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('fica desativado sem registros', async () => {
    api.listarRegistros.mockResolvedValue(page([]));
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    expect((screen.getByRole('button', { name: /Desfazer último/ }) as HTMLButtonElement).disabled).toBe(true);
  });
});

describe('histórico', () => {
  it('agrupa por dia, mostra tipos pelo nome e carrega mais', async () => {
    const maisUm = { ...REGISTRO_ANTIGO, id: 'reg-0', registradoEm: '2026-11-01T10:00:00Z' };
    api.listarRegistros
      .mockResolvedValueOnce(page([REGISTRO, REGISTRO_ANTIGO], 0, 2))
      .mockResolvedValueOnce(page([maisUm], 1, 2));
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await abrirHistorico(user);

    expect(historico().getAllByRole('heading', { level: 3 }).length).toBe(1);
    expect(screen.getByText(/3 células · Micronúcleo, Stickiness/)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Carregar mais' }));
    await waitFor(() => expect(historico().getAllByRole('heading', { level: 3 }).length).toBe(2));
    expect(api.listarRegistros).toHaveBeenLastCalledWith('lamina-1', expect.objectContaining({ page: 1 }));
    expect(screen.queryByRole('button', { name: 'Carregar mais' })).toBeNull();
  });

  it('edita um registro com a versão esperada', async () => {
    api.atualizarRegistro.mockResolvedValue({});
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await abrirHistorico(user);
    await user.click(screen.getAllByRole('button', { name: /^Editar\s*registro/ })[0]);
    const quantity = screen.getByLabelText('Quantidade de células');
    await user.clear(quantity);
    await user.type(quantity, '4');
    await user.click(screen.getByRole('checkbox', { name: 'Stickiness' }));
    await user.click(screen.getByRole('button', { name: 'Salvar alteração' }));

    await waitFor(() => expect(api.atualizarRegistro).toHaveBeenCalledTimes(1));
    expect(api.atualizarRegistro).toHaveBeenCalledWith('lamina-1', 'reg-2', { versaoEsperada: 2, faseId: PROFASE, status: 'ANORMAL', tipoIds: [MICRO], quantidade: 4 });
  });

  it('em 409 avisa e recarrega', async () => {
    api.atualizarRegistro.mockRejectedValue(apiError(409));
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await abrirHistorico(user);
    await user.click(screen.getAllByRole('button', { name: /^Editar\s*registro/ })[0]);
    await user.click(screen.getByRole('button', { name: 'Salvar alteração' }));

    expect((await screen.findByRole('alert')).textContent).toContain('alterado em outro lugar');
    await waitFor(() => expect(api.buscarResumo).toHaveBeenCalledTimes(2));
  });
});

describe('editar e excluir a lâmina', () => {
  it('edita com a versão esperada e mantém as observações', async () => {
    api.atualizarLamina.mockResolvedValue({ ...LAMINA, versao: 4, nome: 'L1 renomeada' });
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(screen.getByRole('button', { name: 'Editar lâmina' }));
    const name = screen.getByLabelText('Identificação da lâmina');
    await user.clear(name);
    await user.type(name, 'L1 renomeada');
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));

    await waitFor(() => expect(api.atualizarLamina).toHaveBeenCalledTimes(1));
    expect(api.atualizarLamina).toHaveBeenCalledWith('lamina-1', {
      versaoEsperada: 3, nome: 'L1 renomeada', especie: 'Allium cepa', tratamento: 'controle', observacoes: 'Nota salva', meta: 1000,
    });
    await screen.findByRole('heading', { name: 'L1 renomeada' });
  });

  describe('observações salvas sozinhas', () => {
    const salvas = { ...LAMINA, versao: 4, observacoes: 'Nota salva e mais' };

    it('salva cerca de 1 s depois da última tecla e mostra "Salvo"', async () => {
      api.atualizarLamina.mockResolvedValue(salvas);
      const user = userEvent.setup();
      renderSlides();
      await openSlide(user);
      expect(screen.queryByRole('button', { name: 'Salvar observações' })).toBeNull();
      await user.type(screen.getByLabelText('Observações da lâmina'), ' e mais');
      expect(api.atualizarLamina).not.toHaveBeenCalled();

      await waitFor(() => expect(api.atualizarLamina).toHaveBeenCalledTimes(1), { timeout: 3000 });
      expect(api.atualizarLamina).toHaveBeenCalledWith('lamina-1', expect.objectContaining({ versaoEsperada: 3, observacoes: 'Nota salva e mais' }));
      expect(await screen.findByText('Salvo')).toBeTruthy();
    });

    it('salva na hora ao sair do campo', async () => {
      api.atualizarLamina.mockResolvedValue(salvas);
      const user = userEvent.setup();
      renderSlides();
      await openSlide(user);
      await user.type(screen.getByLabelText('Observações da lâmina'), ' e mais');
      await user.tab();
      await waitFor(() => expect(api.atualizarLamina).toHaveBeenCalledTimes(1));
    });

    it('salva ao sair da lâmina antes do prazo', async () => {
      api.atualizarLamina.mockResolvedValue(salvas);
      const user = userEvent.setup();
      const { unmount } = renderSlides();
      await openSlide(user);
      await user.type(screen.getByLabelText('Observações da lâmina'), ' e mais');
      unmount();
      await waitFor(() => expect(api.atualizarLamina).toHaveBeenCalledWith('lamina-1', expect.objectContaining({ observacoes: 'Nota salva e mais' })));
    });

    it('se falhar, mantém o texto no campo e deixa tentar de novo', async () => {
      api.atualizarLamina.mockRejectedValueOnce(apiError(500, 'Falha do servidor.')).mockResolvedValue(salvas);
      const user = userEvent.setup();
      renderSlides();
      await openSlide(user);
      const campo = screen.getByLabelText('Observações da lâmina') as HTMLTextAreaElement;
      await user.type(campo, ' e mais');
      await user.tab();

      await user.click(await screen.findByRole('button', { name: 'Tentar de novo' }));
      await waitFor(() => expect(api.atualizarLamina).toHaveBeenCalledTimes(2));
      expect(campo.value).toBe('Nota salva e mais');
      expect(await screen.findByText('Salvo')).toBeTruthy();
    });
  });

  it('em 409 ao editar avisa e recarrega', async () => {
    api.atualizarLamina.mockRejectedValue(apiError(409));
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(screen.getByRole('button', { name: 'Editar lâmina' }));
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));
    expect((await screen.findByRole('alert')).textContent).toContain('alterada em outro lugar');
    await waitFor(() => expect(api.buscarLamina).toHaveBeenCalledTimes(2));
  });

  it('exclui com a versão esperada e volta à lista', async () => {
    api.excluirLamina.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(screen.getByRole('button', { name: 'Excluir lâmina' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: /Excluir lâmina/ }));

    await waitFor(() => expect(api.excluirLamina).toHaveBeenCalledWith('lamina-1', 3));
    expect(await screen.findByRole('heading', { name: 'Lâminas' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /L1 planta A/ })).toBeNull();
  });
});

describe('percentuais', () => {
  it('mostra duas casas decimais, inclusive no índice mitótico', async () => {
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    expect(screen.getByText('66,67%')).toBeTruthy();
    expect(screen.getByText('33,33% do total')).toBeTruthy();
    expect(screen.getByText('Micronúcleo: 1 (50,00%)')).toBeTruthy();
    expect(screen.getAllByText('50,00%').length).toBeGreaterThan(0);
  });
});

describe('células guardadas sem conexão', () => {
  const FILA = 'citogenetica:fila-registros:v1';
  const guardadas = () => JSON.parse(localStorage.getItem(FILA) ?? '[]') as { usuarioId: number; laminaId: string; pedido: { id: string } }[];

  it('falha de rede guarda a célula, mantém a contagem e mostra o aviso fixo', async () => {
    api.criarRegistro.mockRejectedValue(new NetworkError());
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(normalButton());
    await user.click(normalButton());

    expect(await screen.findByText(/Sem internet\. 2 células guardadas neste aparelho/)).toBeTruthy();
    expect(contagemProfase()).toBe('3');
    expect(screen.queryByRole('alert')).toBeNull();
    const salvas = guardadas();
    expect(salvas.map(item => [item.usuarioId, item.laminaId])).toEqual([[ME, 'lamina-1'], [ME, 'lamina-1']]);
    expect(salvas[0].pedido).toEqual(expect.objectContaining({ faseId: PROFASE, status: 'NORMAL', quantidade: 1, horaAparelho: expect.stringMatching(/Z$/), fusoAparelho: expect.any(String) }));
  });

  it('anormal pela janela também é guardada', async () => {
    api.criarRegistro.mockRejectedValue(new NetworkError());
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(abnormalButton());
    await user.click(screen.getByRole('button', { name: 'Marcar várias' }));
    await user.click(screen.getByRole('checkbox', { name: 'Micronúcleo' }));
    await user.click(screen.getByRole('button', { name: /Registrar célula/ }));

    expect(await screen.findByText(/Sem internet\. 1 célula guardada/)).toBeTruthy();
    expect(guardadas()[0].pedido).toEqual(expect.objectContaining({ status: 'ANORMAL', tipoIds: [MICRO] }));
  });

  it('erro que não é de rede segue descartando a célula e avisando', async () => {
    api.criarRegistro.mockRejectedValue(apiError(400, 'Fase inválida.'));
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(normalButton());

    expect((await screen.findByRole('alert')).textContent).toContain('Fase inválida.');
    expect(guardadas()).toEqual([]);
    await waitFor(() => expect(contagemProfase()).toBe('1'));
  });

  it('reenvia em ordem, um por vez, ao voltar a conexão e limpa a fila', async () => {
    api.criarRegistro.mockRejectedValue(new NetworkError());
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await user.click(normalButton());
    await user.click(abnormalButton());
    await user.click(screen.getByRole('button', { name: 'Marcar várias' }));
    await user.click(screen.getByRole('checkbox', { name: 'Micronúcleo' }));
    await user.click(screen.getByRole('button', { name: /Registrar célula/ }));
    await screen.findByText(/2 células guardadas/);
    const ordem = guardadas().map(item => item.pedido.id);

    api.criarRegistro.mockReset();
    api.criarRegistro.mockResolvedValue({});
    api.buscarResumo.mockClear();
    window.dispatchEvent(new Event('online'));

    await waitFor(() => expect(guardadas()).toEqual([]));
    expect(api.criarRegistro.mock.calls.map(call => call[1].id)).toEqual(ordem);
    await waitFor(() => expect(api.buscarResumo).toHaveBeenCalledTimes(1));
    expect(screen.queryByText(/guardadas? neste aparelho/)).toBeNull();
  });

  it('a fila sobrevive a fechar a lâmina e é enviada ao abri-la de novo', async () => {
    api.criarRegistro.mockRejectedValue(new NetworkError());
    const user = userEvent.setup();
    const primeira = renderSlides();
    await openSlide(user);
    await user.click(normalButton());
    await screen.findByText(/1 célula guardada/);
    primeira.unmount();

    api.criarRegistro.mockReset();
    api.criarRegistro.mockResolvedValue({});
    renderSlides();
    await openSlide(user);
    await waitFor(() => expect(api.criarRegistro).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(guardadas()).toEqual([]));
  });

  it('resposta idempotente (200) também limpa a fila', async () => {
    localStorage.setItem(FILA, JSON.stringify([{ usuarioId: ME, laminaId: 'lamina-1', pedido: { id: 'aaaaaaaa-0000-4000-8000-000000000009', faseId: PROFASE, status: 'NORMAL', quantidade: 1 } }]));
    api.criarRegistro.mockResolvedValue(REGISTRO);
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await waitFor(() => expect(guardadas()).toEqual([]));
    expect(api.criarRegistro).toHaveBeenCalledWith('lamina-1', expect.objectContaining({ id: 'aaaaaaaa-0000-4000-8000-000000000009' }));
  });

  it('recusa definitiva avisa qual célula não entrou e a remove da fila', async () => {
    localStorage.setItem(FILA, JSON.stringify([{ usuarioId: ME, laminaId: 'lamina-1', pedido: { id: 'aaaaaaaa-0000-4000-8000-000000000009', faseId: PROFASE, status: 'ANORMAL', tipoIds: [MICRO], quantidade: 1 } }]));
    api.criarRegistro.mockRejectedValue(apiError(400, 'Tipo inválido.'));
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);

    expect((await screen.findByRole('alert')).textContent).toContain('A célula anormal de Prófase não entrou na contagem: Tipo inválido.');
    expect(guardadas()).toEqual([]);
  });

  it('401 mantém a fila guardada', async () => {
    localStorage.setItem(FILA, JSON.stringify([{ usuarioId: ME, laminaId: 'lamina-1', pedido: { id: 'aaaaaaaa-0000-4000-8000-000000000009', faseId: PROFASE, status: 'NORMAL', quantidade: 1 } }]));
    api.criarRegistro.mockRejectedValue(apiError(401, 'Sessão expirada.'));
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);

    expect((await screen.findByRole('alert')).textContent).toContain('Sua sessão expirou');
    expect(guardadas()).toHaveLength(1);
    expect(screen.getByText(/1 célula guardada/)).toBeTruthy();
  });

  it('trava desfazer, ajuste, edição e exclusão enquanto houver fila, mas a contagem segue liberada', async () => {
    localStorage.setItem(FILA, JSON.stringify([{ usuarioId: ME, laminaId: 'lamina-1', pedido: { id: 'aaaaaaaa-0000-4000-8000-000000000009', faseId: PROFASE, status: 'NORMAL', quantidade: 1 } }]));
    api.criarRegistro.mockRejectedValue(new NetworkError());
    const user = userEvent.setup();
    renderSlides();
    await openSlide(user);
    await screen.findByText(/1 célula guardada/);

    const travado = (el: HTMLElement) => (el as HTMLButtonElement).disabled;
    expect(travado(screen.getByRole('button', { name: /Desfazer/ }))).toBe(true);
    expect(travado(screen.getByRole('button', { name: 'Excluir lâmina' }) ?? screen.getByTitle(/Disponível depois/))).toBe(true);
    expect(travado(screen.getByRole('button', { name: /Editar lâmina/ }))).toBe(true);
    expect(travado(screen.getAllByRole('button', { name: /Ajustar quantidade/ })[0])).toBe(true);
    expect(travado(normalButton())).toBe(false);
    expect(travado(abnormalButton())).toBe(false);
    await abrirHistorico(user);
    expect(travado(screen.getAllByRole('button', { name: /Editar/ })[0])).toBe(true);
  });
});

describe('exportação da lâmina', () => {
  const botoes = [
    ['Resumo (CSV)', 'baixarLaminaResumoCsv'],
    ['Registros (CSV)', 'baixarLaminaRegistrosCsv'],
    ['Relatório (PDF)', 'baixarLaminaRelatorioPdf'],
    ['Dados completos (JSON)', 'baixarLaminaJson'],
  ] as const;

  async function abrirResumo(user: ReturnType<typeof userEvent.setup>) {
    renderSlides();
    await openSlide(user);
  }
  async function exportar(user: ReturnType<typeof userEvent.setup>, rotulo: string) {
    await user.click(screen.getByRole('button', { name: 'Exportar' }));
    await user.click(screen.getByRole('menuitem', { name: rotulo }));
  }

  it.each(botoes)('"%s" baixa da API e entrega o arquivo', async (rotulo, funcao) => {
    api[funcao].mockResolvedValue(ARQUIVO);
    const user = userEvent.setup();
    await abrirResumo(user);
    await exportar(user, rotulo);
    await waitFor(() => expect(salvarArquivo).toHaveBeenCalledWith(ARQUIVO));
    expect(api[funcao]).toHaveBeenCalledWith('lamina-1');
    expect(screen.queryByRole('button', { name: /Imprimir/ })).toBeNull();
  });

  it('desabilita os botões enquanto baixa e mostra o progresso', async () => {
    let concluir: (value: typeof ARQUIVO) => void = () => undefined;
    api.baixarLaminaRelatorioPdf.mockReturnValue(new Promise(resolve => { concluir = resolve; }));
    const user = userEvent.setup();
    await abrirResumo(user);
    await exportar(user, 'Relatório (PDF)');
    const ativo = await screen.findByRole('button', { name: 'Baixando…' });
    expect(ativo.getAttribute('aria-busy')).toBe('true');
    expect((ativo as HTMLButtonElement).disabled).toBe(true);
    concluir(ARQUIVO);
    expect(((await screen.findByRole('button', { name: 'Exportar' })) as HTMLButtonElement).disabled).toBe(false);
  });

  it('mostra o erro da API e não entrega arquivo', async () => {
    api.baixarLaminaRegistrosCsv.mockRejectedValue(apiError(500, 'Falha ao gerar o arquivo.'));
    const user = userEvent.setup();
    await abrirResumo(user);
    await exportar(user, 'Registros (CSV)');
    expect((await screen.findByRole('alert')).textContent).toContain('Falha ao gerar o arquivo.');
    expect(salvarArquivo).not.toHaveBeenCalled();
    expect((screen.getByRole('button', { name: 'Exportar' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('explica a falta de conexão', async () => {
    api.baixarLaminaJson.mockRejectedValue(new NetworkError());
    const user = userEvent.setup();
    await abrirResumo(user);
    await exportar(user, 'Dados completos (JSON)');
    expect((await screen.findByRole('alert')).textContent).toContain('Sem conexão');
  });
});

describe('exportação das listas de lâminas', () => {
  const menu = () => screen.queryByRole('button', { name: /exportar/i });
  async function exportar(user: ReturnType<typeof userEvent.setup>, rotulo: string) {
    await user.click(menu()!);
    await user.click(screen.getByRole('menuitem', { name: rotulo }));
  }

  it('um botão só abre o menu com importar e as três exportações; Esc fecha e devolve o foco', async () => {
    const user = userEvent.setup();
    renderSlides({ laboratory: ACTIVE_LAB });
    await screen.findByRole('button', { name: /L1 planta A/ });
    expect(menu()!.textContent).toBe('Importar e exportar');
    await user.click(menu()!);
    expect(screen.getAllByRole('menuitem').map(item => item.textContent))
      .toEqual(['Importar backup antigo', 'Resumo (CSV)', 'Registros (CSV)', 'Dados (JSON)']);
    expect(document.activeElement?.textContent).toBe('Importar backup antigo');
    await user.keyboard('{ArrowDown}');
    expect(document.activeElement?.textContent).toBe('Resumo (CSV)');
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('menu')).toBeNull();
    expect(document.activeElement).toBe(menu());
  });

  it('baixa as três opções com o laboratório da lista', async () => {
    api.baixarLaminasResumoCsv.mockResolvedValue(ARQUIVO);
    api.baixarLaminasRegistrosCsv.mockResolvedValue(ARQUIVO);
    api.baixarLaminasJson.mockResolvedValue(ARQUIVO);
    const user = userEvent.setup();
    renderSlides({ laboratory: ACTIVE_LAB });
    await screen.findByRole('button', { name: /L1 planta A/ });
    await exportar(user, 'Resumo (CSV)');
    await waitFor(() => expect(salvarArquivo).toHaveBeenCalledTimes(1));
    await exportar(user, 'Registros (CSV)');
    await waitFor(() => expect(salvarArquivo).toHaveBeenCalledTimes(2));
    await exportar(user, 'Dados (JSON)');
    await waitFor(() => expect(salvarArquivo).toHaveBeenCalledTimes(3));
    for (const funcao of ['baixarLaminasResumoCsv', 'baixarLaminasRegistrosCsv', 'baixarLaminasJson'] as const) {
      expect(api[funcao]).toHaveBeenCalledWith({ laboratorioId: 'lab-1' });
    }
  });

  it('nas avulsas, não manda laboratório', async () => {
    api.baixarLaminasJson.mockResolvedValue(ARQUIVO);
    const user = userEvent.setup();
    renderSlides();
    await screen.findByRole('button', { name: /L1 planta A/ });
    await exportar(user, 'Dados (JSON)');
    await waitFor(() => expect(api.baixarLaminasJson).toHaveBeenCalledWith({ laboratorioId: null }));
  });

  it('sem lâminas, o menu não oferece exportar', async () => {
    api.listarLaminas.mockResolvedValue(page([]));
    renderSlides();
    await screen.findByText('Comece pela primeira lâmina');
    expect(menu()).toBeNull();
    expect(screen.getByRole('button', { name: 'Importar' })).toBeTruthy();
  });

  it('mostra o erro de acesso negado', async () => {
    api.baixarLaminasResumoCsv.mockRejectedValue(apiError(403));
    const user = userEvent.setup();
    renderSlides({ laboratory: ACTIVE_LAB });
    await screen.findByRole('button', { name: /L1 planta A/ });
    await exportar(user, 'Resumo (CSV)');
    expect((await screen.findByRole('alert')).textContent).toContain('permissão');
  });
});
