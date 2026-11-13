import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode } from 'react';
import { MemoryRouter } from 'react-router';
import type { ProtocoloDTO } from '../src/api/contracts/contagem';
import type { LaboratorioResponseDTO } from '../src/api/contracts/laboratorio';
import type { UsuarioResponseDTO } from '../src/api/contracts/usuario';
import { ApiError, NetworkError } from '../src/api/errors';
import type { Entry, Slide } from '../src/domain';
import { load, save } from '../src/storage';

const labApi = vi.hoisted(() => ({ listarMeus: vi.fn() }));
vi.mock('../src/api/laboratorio-api', () => ({ laboratorioApi: labApi }));
vi.mock('../src/api/convite-api', () => ({ conviteApi: {} }));
vi.mock('../src/api/orientacao-api', () => ({ orientacaoApi: {} }));

import { Laboratories } from '../src/Laboratories';

const VERSAO = 'aaaaaaaa-0000-4000-8000-000000000001';
const LAB_ATIVO = '8d8c8657-599d-4d5e-9f27-f4d62d3a043a';
const LAB_SAIU = '9d8c8657-599d-4d5e-9f27-f4d62d3a043b';
const LAB_DESATIVADO = '7d8c8657-599d-4d5e-9f27-f4d62d3a043c';
const PROTOCOLOS: ProtocoloDTO[] = [
  { id: 'p-mitose', codigo: 'mitose', nome: 'Mitose', versaoAtual: { id: VERSAO, numero: 1, fases: [{ id: 'bbbbbbbb-0000-4000-8000-000000000001', nome: 'Interfase', ordem: 1, contaComoDivisao: false, tipos: [] }] } },
];
const USER = { id: 12, nome: 'Ana', email: 'ana@ufla.br', role: { nome: 'USER' } } as UsuarioResponseDTO;
const lab = (id: string, nome: string, estado: LaboratorioResponseDTO['estado'] = 'ATIVO'): LaboratorioResponseDTO => ({ id, nome, estado, papeis: ['MEMBRO'], versao: 1 });
const entry = (id: string): Entry => ({ id, phase: 'Interfase', status: 'normal', type: '', createdAt: '2025-03-02T10:00:00.000Z' });
const slide = (id: string, name: string, cells: number, extra: Partial<Slide> = {}): Slide => ({
  id, name, protocol: 'mitose', species: '', treatment: '', notes: '', createdAt: '2025-03-01T10:00:00.000Z', entries: Array.from({ length: cells }, (_, i) => entry(`${id}-${i}`)), ...extra,
});

const api = {
  listarProtocolos: vi.fn(), buscarVersaoProtocolo: vi.fn(), listarTiposPersonalizados: vi.fn(), criarTipoPersonalizado: vi.fn(),
  listarLaminas: vi.fn(), contarLaminasPorLaboratorio: vi.fn(), buscarLamina: vi.fn(), criarLamina: vi.fn(), atualizarLamina: vi.fn(), excluirLamina: vi.fn(),
  criarRegistro: vi.fn(), listarRegistros: vi.fn(), atualizarRegistro: vi.fn(), desfazerRegistros: vi.fn(), buscarResumo: vi.fn(),
};
const seed = (slides: Slide[]) => save({ version: 1, slides, customTypes: {} });
const renderLabs = (strict = false) => {
  const page = <MemoryRouter initialEntries={['/laboratorios']}><Laboratories user={USER} onLogout={vi.fn()} onUserUpdate={vi.fn()} renderSlides={() => <p>Lâminas</p>} api={api as never} /></MemoryRouter>;
  return render(strict ? <StrictMode>{page}</StrictMode> : page);
};
const ids = () => load().state.slides.map(item => item.id);
let downloads: Blob[] = [];

beforeEach(() => {
  localStorage.clear();
  vi.resetAllMocks();
  labApi.listarMeus.mockResolvedValue([lab(LAB_ATIVO, 'Citogenética UFLA'), lab(LAB_DESATIVADO, 'Lab parado', 'DESATIVADO')]);
  api.listarProtocolos.mockResolvedValue(PROTOCOLOS);
  api.contarLaminasPorLaboratorio.mockResolvedValue(new Map());
  api.criarLamina.mockImplementation(async (r: unknown) => r);
  api.criarRegistro.mockImplementation(async (_l: string, r: unknown) => r);
  api.buscarResumo.mockImplementation(async (id: string) => ({ total: api.criarRegistro.mock.calls.filter(call => call[0] === id).length }));
});

describe('migração automática das lâminas guardadas só no navegador', () => {
  it('sem lâminas locais não faz nada nem mostra recado', async () => {
    renderLabs();
    await screen.findByText('Citogenética UFLA');
    await Promise.resolve();
    expect(api.listarProtocolos).not.toHaveBeenCalled();
    expect(api.criarLamina).not.toHaveBeenCalled();
    expect(screen.queryByText(/antiga/)).toBeNull();
  });

  it('migra sozinha, sem clique nem tela, manda cada lâmina ao destino com a data original e avisa no fim', async () => {
    seed([slide('s1', 'Cebola A', 2, { labId: LAB_ATIVO }), slide('s2', 'Cebola B', 1, { labId: LAB_SAIU }), slide('s3', 'Cebola C', 1, { labId: LAB_DESATIVADO })]);
    renderLabs();
    expect(await screen.findByText('3 lâminas antigas foram guardadas no sistema.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Guardar|cópia|backup/i })).toBeNull();
    expect(screen.queryByText(/só neste computador/)).toBeNull();
    const [a, b, c] = api.criarLamina.mock.calls.map(call => call[0]);
    expect(a.laboratorioId).toBe(LAB_ATIVO);
    expect(b).not.toHaveProperty('laboratorioId');
    expect(c).not.toHaveProperty('laboratorioId');
    expect(a.criadaEm).toBe('2025-03-01T10:00:00.000Z');
    expect(ids()).toEqual([]);
  });

  it('recado no singular', async () => {
    seed([slide('s1', 'Cebola A', 1)]);
    renderLabs();
    expect(await screen.findByText('1 lâmina antiga foi guardada no sistema.')).toBeTruthy();
  });

  it('não espera a lista de laboratórios carregar com sucesso', async () => {
    seed([slide('s1', 'Cebola A', 1)]);
    labApi.listarMeus.mockRejectedValue(new Error('falhou'));
    renderLabs();
    await screen.findAllByText('falhou');
    expect(api.criarLamina).not.toHaveBeenCalled();
    expect(ids()).toEqual(['s1']);
  });

  it('falha de rede mantém a lâmina, não mostra nada e tenta de novo na próxima abertura', async () => {
    seed([slide('s1', 'Cebola A', 1)]);
    api.criarLamina.mockRejectedValue(new NetworkError());
    const first = renderLabs();
    await waitFor(() => expect(api.criarLamina).toHaveBeenCalledTimes(1));
    await screen.findByText('Citogenética UFLA');
    await waitFor(() => expect(ids()).toEqual(['s1']));
    expect(screen.queryByText(/antiga|falh|erro/i)).toBeNull();
    expect(screen.queryByRole('alert')).toBeNull();
    first.unmount();

    api.criarLamina.mockImplementation(async (r: unknown) => r);
    renderLabs();
    expect(await screen.findByText('1 lâmina antiga foi guardada no sistema.')).toBeTruthy();
    expect(api.criarLamina).toHaveBeenCalledTimes(2);
    expect(ids()).toEqual([]);
  });

  it('erro definitivo de uma lâmina não impede as outras, que ficam para a próxima vez', async () => {
    seed([slide('s1', 'Cebola A', 1), slide('s2', 'Cebola B', 1)]);
    api.criarLamina.mockImplementation(async (r: { nome: string }) => {
      if (r.nome === 'Cebola A') throw new ApiError({ status: 400, error: 'Erro', message: 'Inválida.', path: '/x' });
      return r;
    });
    renderLabs();
    expect(await screen.findByText('1 lâmina antiga foi guardada no sistema.')).toBeTruthy();
    expect(ids()).toEqual(['s1']);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('não roda em dobro com StrictMode', async () => {
    seed([slide('s1', 'Cebola A', 1)]);
    renderLabs(true);
    expect(await screen.findByText('1 lâmina antiga foi guardada no sistema.')).toBeTruthy();
    expect(api.criarLamina).toHaveBeenCalledTimes(1);
  });
});
