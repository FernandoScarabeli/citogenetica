import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { ApiError } from '../src/api/errors';
import type { AdminLaboratorioPageDTO, AdminLaboratorioResponseDTO } from '../src/api/contracts/admin';
import type { UsuarioResponseDTO } from '../src/api/contracts/usuario';

const laboratorioApi = vi.hoisted(() => ({
  listarMeus: vi.fn(), buscar: vi.fn(), criar: vi.fn(), atualizarNome: vi.fn(), listarParticipacoes: vi.fn(),
  alterarPapeis: vi.fn(), removerParticipacao: vi.fn(), sair: vi.fn(),
}));
vi.mock('../src/api/laboratorio-api', () => ({ laboratorioApi }));

import { AdminLaboratories } from '../src/app/AdminLaboratoriesPage';
import { Laboratories } from '../src/Laboratories';

function userWith(role: 'ADMIN' | 'USER'): UsuarioResponseDTO {
  return {
    id: 1, version: 0, createdDate: '2026-11-03T12:00:00', lastModifiedDate: null,
    nome: 'Ana Pesquisadora', email: 'ana@ufla.br', status: 'ATIVO',
    role: { id: 1, version: 0, createdDate: '2026-11-03T12:00:00', lastModifiedDate: null, nome: role },
  };
}

const ACTIVE: AdminLaboratorioResponseDTO = {
  id: 'lab-ativo', nome: 'Citogenética Vegetal', estado: 'ATIVO', quantidadeMembrosAtivos: 4,
  administradores: [{ nome: 'Bruna Almeida', email: 'bruna@ufla.br' }],
};
const DISABLED: AdminLaboratorioResponseDTO = {
  id: 'lab-off', nome: 'Genética Animal', estado: 'DESATIVADO', quantidadeMembrosAtivos: 2, administradores: [],
};

function pageOf(content: AdminLaboratorioResponseDTO[], currentPage = 0, totalPages = 1): AdminLaboratorioPageDTO {
  return { content, currentPage, totalPages, totalElements: content.length };
}

function apiError(status: number) {
  return new ApiError({ status, error: 'Erro', message: 'Falha do servidor.', path: '/api/v1/admin/laboratorios' });
}

const api = {
  listarLaboratorios: vi.fn(),
  desativarLaboratorio: vi.fn(),
  reativarLaboratorio: vi.fn(),
};

function renderAdmin(role: 'ADMIN' | 'USER' = 'ADMIN') {
  return render(<MemoryRouter initialEntries={['/admin/laboratorios']}>
    <AdminLaboratories user={userWith(role)} onLogout={vi.fn()} api={api} />
  </MemoryRouter>);
}

beforeEach(() => {
  vi.resetAllMocks();
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value(this: HTMLDialogElement) { this.setAttribute('open', ''); } });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value(this: HTMLDialogElement) { this.removeAttribute('open'); } });
  api.listarLaboratorios.mockResolvedValue(pageOf([ACTIVE, DISABLED]));
  api.desativarLaboratorio.mockResolvedValue(undefined);
  api.reativarLaboratorio.mockResolvedValue(undefined);
});

describe('menu da conta', () => {
  beforeEach(() => {
    laboratorioApi.listarMeus.mockResolvedValue([]);
  });

  function renderLabs(role: 'ADMIN' | 'USER') {
    return render(<MemoryRouter initialEntries={['/laboratorios']}>
      <Laboratories user={userWith(role)} onLogout={vi.fn()} onUserUpdate={vi.fn()} renderSlides={() => null} />
    </MemoryRouter>);
  }

  it('mostra o link da administração só para o admin geral', async () => {
    const user = userEvent.setup();
    const { unmount } = renderLabs('ADMIN');
    await user.click(await screen.findByRole('button', { name: 'Menu do perfil' }));
    expect(screen.getByRole('link', { name: 'Administração da plataforma' }).getAttribute('href')).toBe('/admin/laboratorios');
    unmount();

    renderLabs('USER');
    await user.click(await screen.findByRole('button', { name: 'Menu do perfil' }));
    expect(screen.queryByRole('link', { name: 'Administração da plataforma' })).toBeNull();
  });
});

describe('página de laboratórios da plataforma', () => {
  it('mostra sem permissão para usuário comum e não chama a API de admin', async () => {
    renderAdmin('USER');
    expect(await screen.findByRole('heading', { name: 'Sem permissão para acessar esta página' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Voltar para Meus laboratórios' }).getAttribute('href')).toBe('/laboratorios');
    expect(api.listarLaboratorios).not.toHaveBeenCalled();
  });

  it('mostra sem permissão quando o servidor responde 403 na listagem', async () => {
    api.listarLaboratorios.mockRejectedValue(apiError(403));
    renderAdmin();
    expect(await screen.findByRole('heading', { name: 'Sem permissão para acessar esta página' })).toBeTruthy();
  });

  it('lista nome, estado, membros e administradores com mailto', async () => {
    renderAdmin();
    const table = await screen.findByRole('table');
    expect(within(table).getByText('Citogenética Vegetal')).toBeTruthy();
    expect(within(table).getByRole('link', { name: 'bruna@ufla.br' }).getAttribute('href')).toBe('mailto:bruna@ufla.br');
    expect(within(table).getByText('Sem administrador ativo')).toBeTruthy();
    expect(within(table).getByRole('button', { name: 'Desativar Citogenética Vegetal' })).toBeTruthy();
    expect(within(table).getByRole('button', { name: 'Reativar Genética Animal' })).toBeTruthy();
  });

  it('mostra erro e tenta de novo', async () => {
    api.listarLaboratorios.mockRejectedValueOnce(apiError(500));
    renderAdmin();
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Tentar de novo' }));
    expect(await screen.findByRole('table')).toBeTruthy();
    expect(api.listarLaboratorios).toHaveBeenCalledTimes(2);
  });

  it('diferencia lista vazia de busca sem resultado', async () => {
    api.listarLaboratorios.mockResolvedValue(pageOf([], 0, 0));
    renderAdmin();
    expect(await screen.findByRole('heading', { name: 'Nenhum laboratório cadastrado' })).toBeTruthy();
    const user = userEvent.setup();
    await user.type(screen.getByRole('searchbox', { name: 'Buscar laboratório por nome' }), 'xyz{Enter}');
    expect(await screen.findByRole('heading', { name: 'Nenhum resultado para a busca' })).toBeTruthy();
  });

  it('busca só ao enviar o formulário e volta à primeira página', async () => {
    api.listarLaboratorios.mockResolvedValue(pageOf([ACTIVE], 0, 3));
    const user = userEvent.setup();
    renderAdmin();
    await screen.findByRole('table');
    await user.click(screen.getByRole('button', { name: 'Próxima' }));
    await waitFor(() => expect(api.listarLaboratorios).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })));
    const calls = api.listarLaboratorios.mock.calls.length;
    await user.type(screen.getByRole('searchbox', { name: 'Buscar laboratório por nome' }), 'veg');
    expect(api.listarLaboratorios).toHaveBeenCalledTimes(calls);
    await user.click(screen.getByRole('button', { name: 'Buscar' }));
    await waitFor(() => expect(api.listarLaboratorios).toHaveBeenLastCalledWith(expect.objectContaining({ nome: 'veg', page: 0 })));
  });

  it('pagina para a próxima e a anterior', async () => {
    api.listarLaboratorios.mockResolvedValue(pageOf([ACTIVE], 0, 2));
    const user = userEvent.setup();
    renderAdmin();
    await screen.findByRole('table');
    expect((screen.getByRole('button', { name: /Anterior/ }) as HTMLButtonElement).disabled).toBe(true);
    await user.click(screen.getByRole('button', { name: 'Próxima' }));
    await waitFor(() => expect(api.listarLaboratorios).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })));
    await user.click(await screen.findByRole('button', { name: 'Anterior' }));
    await waitFor(() => expect(api.listarLaboratorios).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 })));
  });

  it('filtra por estado e volta à primeira página', async () => {
    api.listarLaboratorios.mockResolvedValue(pageOf([ACTIVE], 0, 3));
    const user = userEvent.setup();
    renderAdmin();
    await screen.findByRole('table');
    await user.click(screen.getByRole('button', { name: 'Próxima' }));
    await waitFor(() => expect(api.listarLaboratorios).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })));
    await user.click(screen.getByRole('button', { name: 'Desativados' }));
    await waitFor(() => expect(api.listarLaboratorios).toHaveBeenLastCalledWith(expect.objectContaining({ estado: 'DESATIVADO', page: 0 })));
    expect(screen.getByRole('button', { name: 'Desativados' }).getAttribute('aria-pressed')).toBe('true');
  });

  it('desativa depois de confirmar e recarrega a lista', async () => {
    const user = userEvent.setup();
    renderAdmin();
    await user.click(await screen.findByRole('button', { name: 'Desativar Citogenética Vegetal' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText(/ninguém poderá alterá-lo nem enviar convites/)).toBeTruthy();
    await user.click(within(dialog).getByRole('button', { name: 'Desativar laboratório' }));
    await waitFor(() => expect(api.desativarLaboratorio).toHaveBeenCalledWith('lab-ativo'));
    expect(await screen.findByText('Citogenética Vegetal foi desativado.')).toBeTruthy();
    expect(api.listarLaboratorios).toHaveBeenCalledTimes(2);
  });

  it('reativa depois de confirmar', async () => {
    const user = userEvent.setup();
    renderAdmin();
    await user.click(await screen.findByRole('button', { name: 'Reativar Genética Animal' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText(/volta a aceitar alterações e convites/)).toBeTruthy();
    await user.click(within(dialog).getByRole('button', { name: 'Reativar laboratório' }));
    await waitFor(() => expect(api.reativarLaboratorio).toHaveBeenCalledWith('lab-off'));
    expect(await screen.findByText('Genética Animal foi reativado.')).toBeTruthy();
  });

  it('cancelar a confirmação não chama a API', async () => {
    const user = userEvent.setup();
    renderAdmin();
    await user.click(await screen.findByRole('button', { name: 'Desativar Citogenética Vegetal' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }));
    expect(api.desativarLaboratorio).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('explica o 403 em ação sem fechar a confirmação', async () => {
    api.desativarLaboratorio.mockRejectedValue(apiError(403));
    const user = userEvent.setup();
    renderAdmin();
    await user.click(await screen.findByRole('button', { name: 'Desativar Citogenética Vegetal' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Desativar laboratório' }));
    expect((await screen.findByRole('alert')).textContent).toBe('Você não tem permissão para esta ação.');
  });

  it('explica o 409 ao reativar laboratório sem administrador ativo', async () => {
    api.reativarLaboratorio.mockRejectedValue(apiError(409));
    const user = userEvent.setup();
    renderAdmin();
    await user.click(await screen.findByRole('button', { name: 'Reativar Genética Animal' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Reativar laboratório' }));
    expect((await screen.findByRole('alert')).textContent).toBe('Não é possível reativar um laboratório sem administrador ativo.');
  });

  it('avisa no 404 e recarrega a lista', async () => {
    api.desativarLaboratorio.mockRejectedValue(apiError(404));
    const user = userEvent.setup();
    renderAdmin();
    await user.click(await screen.findByRole('button', { name: 'Desativar Citogenética Vegetal' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Desativar laboratório' }));
    expect((await screen.findByRole('alert')).textContent).toContain('não existe mais');
    await waitFor(() => expect(api.listarLaboratorios).toHaveBeenCalledTimes(2));
  });

  it('volta uma página quando a atual fica vazia depois da ação', async () => {
    api.listarLaboratorios.mockImplementation(async ({ page }: { page: number }) => {
      if (page === 0) return pageOf([DISABLED], 0, 2);
      return api.desativarLaboratorio.mock.calls.length ? pageOf([], 0, 0) : pageOf([ACTIVE], 1, 2);
    });
    const user = userEvent.setup();
    renderAdmin();
    await screen.findByRole('table');
    await user.click(screen.getByRole('button', { name: 'Próxima' }));
    await user.click(await screen.findByRole('button', { name: 'Desativar Citogenética Vegetal' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Desativar laboratório' }));
    await waitFor(() => expect(api.listarLaboratorios).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 })));
    expect(await screen.findByText('Genética Animal')).toBeTruthy();
  });

  it('não deixa resposta antiga sobrescrever a busca mais nova', async () => {
    let resolveFirst: (value: AdminLaboratorioPageDTO) => void = () => undefined;
    api.listarLaboratorios
      .mockImplementationOnce(() => new Promise(resolve => { resolveFirst = resolve; }))
      .mockResolvedValueOnce(pageOf([DISABLED]));
    const user = userEvent.setup();
    renderAdmin();
    await user.type(screen.getByRole('searchbox', { name: 'Buscar laboratório por nome' }), 'gen{Enter}');
    expect(await screen.findByText('Genética Animal')).toBeTruthy();
    resolveFirst(pageOf([ACTIVE]));
    await Promise.resolve();
    expect(screen.queryByText('Citogenética Vegetal')).toBeNull();
  });
});
