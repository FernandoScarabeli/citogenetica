import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import type { LaminaDTO, ProtocoloDTO } from '../src/api/contracts/contagem';
import { ApiError } from '../src/api/errors';
import type { Entry, Slide, State } from '../src/domain';
import { Slides } from '../src/Slides';
import { load, save } from '../src/storage';

const VERSAO = 'aaaaaaaa-0000-4000-8000-000000000001';
const INTERFASE = 'bbbbbbbb-0000-4000-8000-000000000001';
const PROTOCOLOS: ProtocoloDTO[] = [
  { id: 'p-mitose', codigo: 'mitose', nome: 'Mitose', versaoAtual: { id: VERSAO, numero: 1, fases: [{ id: INTERFASE, nome: 'Interfase', ordem: 1, contaComoDivisao: false, tipos: [] }] } },
];
const entry = (id: string): Entry => ({ id, phase: 'Interfase', status: 'normal', type: '', createdAt: '2025-03-02T10:00:00.000Z' });
const slide = (id: string, name: string, cells: number, extra: Partial<Slide> = {}): Slide => ({
  id, name, protocol: 'mitose', species: '', treatment: '', notes: '', createdAt: '2025-03-01T10:00:00.000Z', entries: Array.from({ length: cells }, (_, i) => entry(`${id}-${i}`)), ...extra,
});
const page = <T,>(content: T[]) => ({ content, currentPage: 0, totalPages: 1, totalElements: content.length });
const remote: LaminaDTO = {
  id: 'r1', versao: 1, donoUsuarioId: 12, donoNome: 'Ana', laboratorioId: null, protocoloVersaoId: VERSAO, nome: 'Já no servidor', especie: '', tratamento: '',
  observacoes: '', meta: null, totalCelulas: 4, criadaEm: '2026-11-03T12:00:00Z', atualizadaEm: '2026-11-03T12:00:00Z',
};

const api = {
  listarProtocolos: vi.fn(), buscarVersaoProtocolo: vi.fn(), listarTiposPersonalizados: vi.fn(), criarTipoPersonalizado: vi.fn(),
  listarLaminas: vi.fn(), buscarLamina: vi.fn(), criarLamina: vi.fn(), atualizarLamina: vi.fn(), excluirLamina: vi.fn(),
  criarRegistro: vi.fn(), listarRegistros: vi.fn(), atualizarRegistro: vi.fn(), desfazerRegistros: vi.fn(), buscarResumo: vi.fn(),
};
const seed = (slides: Slide[]) => save({ version: 1, slides, customTypes: {} });
const renderSlides = (readOnly = false) => render(<MemoryRouter><Slides laboratory={null} onBack={vi.fn()} readOnly={readOnly} usuarioId={12} api={api as never} /></MemoryRouter>);
const stateFile = (state: State, name = 'backup.json') => new File([JSON.stringify(state)], name, { type: 'application/json' });
let downloads: Blob[];

beforeEach(() => {
  localStorage.clear();
  vi.resetAllMocks();
  downloads = [];
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: (blob: Blob) => { downloads.push(blob); return 'blob:x'; } });
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: () => undefined });
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  api.listarProtocolos.mockResolvedValue(PROTOCOLOS);
  api.listarLaminas.mockResolvedValue(page([remote]));
  api.criarLamina.mockImplementation(async (r: unknown) => r);
  api.criarRegistro.mockImplementation(async (_l: string, r: unknown) => r);
  api.buscarResumo.mockImplementation(async () => ({ total: api.criarRegistro.mock.calls.length }));
});

describe('lâminas guardadas só no navegador', () => {
  it('não mostra o painel quando não há lâminas locais', async () => {
    renderSlides();
    await screen.findByText('Já no servidor');
    expect(screen.queryByText(/guardadas? só neste navegador/)).toBeNull();
  });

  it('não mostra o painel em modo somente leitura', async () => {
    seed([slide('s1', 'Cebola A', 2)]);
    renderSlides(true);
    await screen.findByText('Já no servidor');
    expect(screen.queryByText(/guardadas? só neste navegador/)).toBeNull();
    expect(screen.queryByRole('button', { name: 'Enviar para o servidor' })).toBeNull();
  });

  it('lista cada lâmina com o número de células e trava o envio até baixar o backup', async () => {
    seed([slide('s1', 'Cebola A', 2), slide('s2', 'Cebola B', 1)]);
    const user = userEvent.setup();
    renderSlides();
    await screen.findByRole('heading', { name: '2 lâminas guardadas só neste navegador' });
    expect(within(screen.getByRole('list')).getByText('Cebola A')).toBeTruthy();
    expect(screen.getByText('2 células')).toBeTruthy();
    expect(screen.getByText('1 célula')).toBeTruthy();
    const send = screen.getByRole('button', { name: 'Enviar para o servidor' }) as HTMLButtonElement;
    expect(send.disabled).toBe(true);

    await user.click(screen.getByRole('button', { name: /Baixar backup/ }));
    expect(send.disabled).toBe(false);
    expect(downloads).toHaveLength(1);
    const backup = JSON.parse(await downloads[0].text()) as State;
    expect(backup.slides.map(item => item.id)).toEqual(['s1', 's2']);
  });

  it('envia, mostra o resultado, recarrega a lista e retira só o que foi confirmado', async () => {
    seed([slide('s1', 'Cebola A', 2), slide('s2', 'Cebola B', 1)]);
    api.criarLamina.mockImplementation(async (r: { nome: string }) => {
      if (r.nome === 'Cebola B') throw new ApiError({ status: 400, error: 'Erro', message: 'Identificação inválida.', path: '/x' });
      return r;
    });
    const user = userEvent.setup();
    renderSlides();
    await user.click(await screen.findByRole('button', { name: /Baixar backup/ }));
    await user.click(screen.getByRole('button', { name: 'Enviar para o servidor' }));

    const report = await screen.findByRole('status', { name: 'Resultado do envio' });
    expect(report.textContent).toContain('1 lâmina enviada');
    expect(report.textContent).toContain('1 lâmina não foi enviada e continua neste navegador');
    expect(report.textContent).toContain('Cebola B: Identificação inválida.');
    expect(api.listarLaminas).toHaveBeenCalledTimes(2);
    expect(load().state.slides.map(item => item.id)).toEqual(['s2']);
    expect(await screen.findByRole('heading', { name: '1 lâmina guardada só neste navegador' })).toBeTruthy();
  });

  it('trava os botões e avisa antes de fechar a página durante o envio', async () => {
    seed([slide('s1', 'Cebola A', 1)]);
    let release: (value: unknown) => void = () => undefined;
    api.criarLamina.mockImplementation(() => new Promise(resolve => { release = resolve; }));
    const user = userEvent.setup();
    renderSlides();
    await user.click(await screen.findByRole('button', { name: /Baixar backup/ }));
    await user.click(screen.getByRole('button', { name: 'Enviar para o servidor' }));

    await waitFor(() => expect(screen.getByText('Enviando lâmina 1 de 1: Cebola A.')).toBeTruthy());
    expect((screen.getByRole('button', { name: 'Enviar para o servidor' }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: /Baixar backup/ }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: /Importar backup/ }) as HTMLButtonElement).disabled).toBe(true);
    const event = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);

    release({});
    await screen.findByRole('status', { name: 'Resultado do envio' });
    const after = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(after);
    expect(after.defaultPrevented).toBe(false);
  });

  it('dado ilegível mostra o erro e não oferece envio nem importação', async () => {
    localStorage.setItem('citogenetica:workspace:v1', '{"version":1,"slides":"quebrado"}');
    renderSlides();
    const alert = await screen.findByText(/Não foi possível ler os dados salvos/);
    expect(alert).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Enviar para o servidor' })).toBeNull();
    expect(screen.queryByRole('button', { name: /Importar backup/ })).toBeNull();
    expect(localStorage.getItem('citogenetica:workspace:v1')).toBe('{"version":1,"slides":"quebrado"}');
  });
});

describe('importar backup', () => {
  it('mescla por id sem sobrescrever e informa quantas entraram', async () => {
    seed([slide('s1', 'Atual', 1)]);
    const user = userEvent.setup();
    renderSlides();
    await screen.findByRole('heading', { name: '1 lâmina guardada só neste navegador' });
    const backup: State = { version: 1, slides: [slide('s1', 'Antiga', 5), slide('s2', 'Recuperada', 3)], customTypes: {} };
    await user.upload(screen.getByLabelText('Arquivo de backup'), stateFile(backup));

    expect(await screen.findByText(/1 lâmina voltou para este navegador/)).toBeTruthy();
    expect(load().state.slides.map(item => `${item.id}:${item.name}`)).toEqual(['s1:Atual', 's2:Recuperada']);
    expect(await screen.findByRole('heading', { name: '2 lâminas guardadas só neste navegador' })).toBeTruthy();
  });

  it('recusa arquivo inválido sem alterar nada', async () => {
    seed([slide('s1', 'Atual', 1)]);
    const user = userEvent.setup();
    renderSlides();
    await screen.findByRole('heading', { name: '1 lâmina guardada só neste navegador' });
    const before = localStorage.getItem('citogenetica:workspace:v1');
    await user.upload(screen.getByLabelText('Arquivo de backup'), new File(['{"version":2}'], 'x.json', { type: 'application/json' }));
    expect(await screen.findByText(/não é um backup válido/)).toBeTruthy();
    await user.upload(screen.getByLabelText('Arquivo de backup'), new File(['não é json'], 'y.json', { type: 'application/json' }));
    expect(await screen.findByText(/Não foi possível ler o arquivo/)).toBeTruthy();
    expect(localStorage.getItem('citogenetica:workspace:v1')).toBe(before);
  });

  it('falha ao gravar vira mensagem', async () => {
    seed([slide('s1', 'Atual', 1)]);
    const user = userEvent.setup();
    renderSlides();
    await screen.findByRole('heading', { name: '1 lâmina guardada só neste navegador' });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('cheio'); });
    await user.upload(screen.getByLabelText('Arquivo de backup'), stateFile({ version: 1, slides: [slide('s2', 'Nova', 1)], customTypes: {} }));
    expect(await screen.findByText(/Não foi possível gravar neste navegador/)).toBeTruthy();
    expect(load().state.slides.map(item => item.id)).toEqual(['s1']);
  });
});
