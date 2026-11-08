import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { ApiError } from '../src/api/errors';
import type { LaboratorioResponseDTO, ParticipacaoResponseDTO } from '../src/api/contracts/laboratorio';
import type { UsuarioResponseDTO } from '../src/api/contracts/usuario';

const api = vi.hoisted(() => ({
  listarMeus: vi.fn(),
  buscar: vi.fn(),
  criar: vi.fn(),
  atualizarNome: vi.fn(),
  listarParticipacoes: vi.fn(),
  alterarPapeis: vi.fn(),
  removerParticipacao: vi.fn(),
  sair: vi.fn(),
}));
const invitationsApi = vi.hoisted(() => ({ criar: vi.fn(), listar: vi.fn(), reenviar: vi.fn(), revogar: vi.fn(), aceitar: vi.fn(), recusar: vi.fn() }));
const orientationApi = vi.hoisted(() => ({
  listarVinculos: vi.fn(), encerrarVinculo: vi.fn(), criarConvite: vi.fn(), listarConvites: vi.fn(),
  reenviarConvite: vi.fn(), cancelarConvite: vi.fn(), aceitarConvite: vi.fn(), recusarConvite: vi.fn(),
}));

vi.mock('../src/api/laboratorio-api', () => ({ laboratorioApi: api }));
vi.mock('../src/api/convite-api', () => ({ conviteApi: invitationsApi }));
vi.mock('../src/api/orientacao-api', () => ({ orientacaoApi: orientationApi }));

import { Laboratories } from '../src/Laboratories';

const USER: UsuarioResponseDTO = {
  id: 12,
  version: 0,
  createdDate: '2026-11-03T12:00:00',
  lastModifiedDate: null,
  nome: 'Ana Pesquisadora',
  email: 'ana@ufla.br',
  status: 'ATIVO',
  role: { id: 1, version: 0, createdDate: '2026-11-03T12:00:00', lastModifiedDate: null, nome: 'USER' },
};

const ACTIVE_LAB: LaboratorioResponseDTO = {
  id: '8d8c8657-599d-4d5e-9f27-f4d62d3a043a',
  nome: 'Laboratório de Citogenética',
  estado: 'ATIVO',
  papeis: ['MEMBRO', 'ADMIN'],
  versao: 2,
};

const SELF: ParticipacaoResponseDTO = {
  id: '3ea2e25b-4e57-4275-a3d3-faf6ceec7428',
  nome: USER.nome,
  email: USER.email,
  papeis: ['MEMBRO', 'ADMIN'],
  versao: 1,
};

const OTHER: ParticipacaoResponseDTO = {
  id: '2ea2e25b-4e57-4275-a3d3-faf6ceec7428',
  nome: 'Bruna Almeida',
  email: 'bruna@ufla.br',
  papeis: ['MEMBRO', 'ORIENTADOR'],
  versao: 4,
};

function apiError(status: number, message = 'A operação não pode ser concluída.', retryAfterSeconds?: number) {
  return new ApiError({ status, error: 'Conflict', message, path: '/api/v1/laboratorios' }, retryAfterSeconds);
}

const PENDING_INVITATION = {
  id: '8b1acdb2-8608-4b1e-bca7-ea4bb074c1b2',
  laboratorioId: ACTIVE_LAB.id,
  emailDestino: 'bruna@ufla.br',
  papeis: ['MEMBRO', 'ORIENTADOR'] as const,
  estado: 'PENDENTE' as const,
  expiraEm: '2026-11-10T18:30:00Z',
  criadoEm: '2026-11-03T18:30:00Z',
};

function renderPage(renderSlides = vi.fn(() => <p>Lâminas</p>)) {
  return render(<MemoryRouter initialEntries={['/laboratorios']}>
    <Laboratories user={USER} onLogout={vi.fn()} onUserUpdate={vi.fn()} renderSlides={renderSlides} />
  </MemoryRouter>);
}

async function openLaboratoryManagement(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole('heading', { name: 'Meus laboratórios' });
  await user.click(screen.getByRole('button', { name: 'Gerenciar' }));
  await screen.findByRole('heading', { name: 'Equipe do laboratório' });
}

async function openInvitations(user: ReturnType<typeof userEvent.setup>) {
  await openLaboratoryManagement(user);
  await user.click(screen.getByRole('button', { name: 'Convites' }));
  await screen.findByRole('heading', { name: 'Convites pendentes' });
}

function defineDialogSupport() {
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
    configurable: true,
    value(this: HTMLDialogElement) { this.setAttribute('open', ''); },
  });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', {
    configurable: true,
    value(this: HTMLDialogElement) { this.removeAttribute('open'); },
  });
}

beforeEach(() => {
  vi.resetAllMocks();
  defineDialogSupport();
  api.listarMeus.mockResolvedValue([ACTIVE_LAB]);
  api.buscar.mockResolvedValue(ACTIVE_LAB);
  api.criar.mockResolvedValue(ACTIVE_LAB);
  api.atualizarNome.mockResolvedValue(ACTIVE_LAB);
  api.listarParticipacoes.mockResolvedValue([SELF, OTHER]);
  api.alterarPapeis.mockImplementation(async (_labId, participationId, _version, papeis) => ({
    ...(participationId === SELF.id ? SELF : OTHER),
    papeis,
    versao: (participationId === SELF.id ? SELF : OTHER).versao + 1,
  }));
  api.removerParticipacao.mockResolvedValue(undefined);
  api.sair.mockResolvedValue(undefined);
  invitationsApi.criar.mockResolvedValue(PENDING_INVITATION);
  invitationsApi.listar.mockResolvedValue([PENDING_INVITATION]);
  invitationsApi.reenviar.mockResolvedValue({ ...PENDING_INVITATION, expiraEm: '2026-11-17T18:30:00Z' });
  invitationsApi.revogar.mockResolvedValue(undefined);
  orientationApi.listarVinculos.mockResolvedValue([{
    id: 'vinculo-1', laboratorioId: ACTIVE_LAB.id, orientadorUsuarioId: 44, nomeOrientador: 'Bruna Almeida',
    pesquisadorUsuarioId: USER.id, nomePesquisador: USER.nome, criadoEm: '2026-10-20T12:00:00Z',
  }]);
  orientationApi.listarConvites.mockResolvedValue([]);
  orientationApi.encerrarVinculo.mockResolvedValue(undefined);
  orientationApi.criarConvite.mockResolvedValue(undefined);
  orientationApi.reenviarConvite.mockResolvedValue(undefined);
  orientationApi.cancelarConvite.mockResolvedValue(undefined);
  window.history.replaceState(null, '', '/laboratorios');
});

describe('gestão de laboratórios reais', () => {
  it('convida por e-mail com Membro sempre incluído e sem papéis adicionais por padrão', async () => {
    const user = userEvent.setup();
    renderPage();
    await openInvitations(user);

    const memberRole = screen.getByRole('checkbox', { name: /Membro/ }) as HTMLInputElement;
    expect(memberRole.checked).toBe(true);
    expect(memberRole.disabled).toBe(true);
    await user.type(screen.getByLabelText('E-mail'), 'nova@ufla.br');
    await user.click(screen.getByRole('button', { name: 'Convidar' }));

    await waitFor(() => expect(invitationsApi.criar).toHaveBeenCalledWith(ACTIVE_LAB.id, 'nova@ufla.br', ['MEMBRO']));
    expect((await screen.findByRole('status')).textContent).toContain('Convite enviado para bruna@ufla.br.');
  });

  it('convida com os dois papéis adicionais opcionais', async () => {
    const user = userEvent.setup();
    renderPage();
    await openInvitations(user);
    await user.type(screen.getByLabelText('E-mail'), 'docente@ufla.br');
    await user.click(screen.getByRole('checkbox', { name: /Administrador/ }));
    await user.click(screen.getByRole('checkbox', { name: /Orientador/ }));
    await user.click(screen.getByRole('button', { name: 'Convidar' }));

    await waitFor(() => expect(invitationsApi.criar).toHaveBeenCalledWith(ACTIVE_LAB.id, 'docente@ufla.br', ['MEMBRO', 'ADMIN', 'ORIENTADOR']));
  });

  it.each([
    'Este endereço já participa ativamente do laboratório.',
    'Já existe convite pendente para este endereço; use o reenvio.',
  ])('orienta a conferir a lista e reenviar após conflito 409: %s', async message => {
    const user = userEvent.setup();
    invitationsApi.criar.mockRejectedValueOnce(apiError(409, message));
    renderPage();
    await openInvitations(user);
    await user.type(screen.getByLabelText('E-mail'), 'bruna@ufla.br');
    await user.click(screen.getByRole('button', { name: 'Convidar' }));

    expect((await screen.findByRole('alert')).textContent).toContain('Confira a lista e use Reenviar');
    expect(await screen.findByRole('button', { name: 'Reenviar' })).toBeTruthy();
  });

  it('mostra Retry-After e bloqueia o envio enquanto a contagem regressiva corre', async () => {
    const user = userEvent.setup();
    invitationsApi.criar.mockRejectedValueOnce(apiError(429, 'Muitas solicitações.', 3));
    renderPage();
    await openInvitations(user);
    await user.type(screen.getByLabelText('E-mail'), 'nova@ufla.br');
    await user.click(screen.getByRole('button', { name: 'Convidar' }));

    expect((await screen.findByRole('alert')).textContent).toContain('Tente novamente em 3 segundos.');
    expect((screen.getByRole('button', { name: 'Aguarde 3 s' }) as HTMLButtonElement).disabled).toBe(true);
    expect(invitationsApi.criar).toHaveBeenCalledTimes(1);
  });

  it('lista papéis e validade, reenvia e exige confirmação acessível para cancelar', async () => {
    const user = userEvent.setup();
    renderPage();
    await openInvitations(user);

    expect(await screen.findByText('bruna@ufla.br')).toBeTruthy();
    expect(screen.getByText(/Válido até/).textContent).toContain('10 de nov. de 2026');
    expect(screen.getAllByText('Orientador').length).toBeGreaterThan(0);
    await user.click(screen.getByRole('button', { name: 'Reenviar' }));
    await waitFor(() => expect(invitationsApi.reenviar).toHaveBeenCalledWith(ACTIVE_LAB.id, PENDING_INVITATION.id));
    expect((await screen.findByRole('status')).textContent).toContain('Convite reenviado para bruna@ufla.br.');

    await user.click(screen.getByRole('button', { name: 'Cancelar convite para bruna@ufla.br' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Cancelar convite?' })).toBeTruthy();
    expect(within(dialog).getByText(/deixará de ser válido/)).toBeTruthy();
    expect(document.activeElement).toBe(within(dialog).getByRole('button', { name: 'Manter convite' }));
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar convite' }));

    await waitFor(() => expect(invitationsApi.revogar).toHaveBeenCalledWith(ACTIVE_LAB.id, PENDING_INVITATION.id));
    expect(await screen.findByText('Não há convites pendentes.')).toBeTruthy();
  });

  it('não oferece convites a quem não é administrador nem em laboratório desativado', async () => {
    const memberOnlyLab = { ...ACTIVE_LAB, papeis: ['MEMBRO'] as const };
    api.listarMeus.mockResolvedValue([memberOnlyLab]);
    api.buscar.mockResolvedValue(memberOnlyLab);
    const user = userEvent.setup();
    const firstPage = renderPage();
    await openLaboratoryManagement(user);
    expect(screen.queryByRole('button', { name: 'Convites' })).toBeNull();
    expect(invitationsApi.listar).not.toHaveBeenCalled();
    firstPage.unmount();

    const disabledLab = { ...ACTIVE_LAB, estado: 'DESATIVADO' as const };
    api.listarMeus.mockResolvedValue([disabledLab]);
    api.buscar.mockResolvedValue(disabledLab);
    const secondUser = userEvent.setup();
    renderPage();
    await openLaboratoryManagement(secondUser);
    expect(screen.queryByRole('button', { name: 'Convites' })).toBeNull();
    expect(invitationsApi.listar).not.toHaveBeenCalled();
  });

  it('abre a seção de orientação usando os vínculos visíveis que a API retorna', async () => {
    const memberOnlyLab = { ...ACTIVE_LAB, papeis: ['MEMBRO'] as const };
    api.listarMeus.mockResolvedValue([memberOnlyLab]);
    api.buscar.mockResolvedValue(memberOnlyLab);
    const user = userEvent.setup();
    renderPage();
    await openLaboratoryManagement(user);
    await user.click(screen.getByRole('button', { name: 'Orientação' }));

    expect(await screen.findByRole('heading', { name: 'Vínculos ativos' })).toBeTruthy();
    expect(within(screen.getByRole('region', { name: 'Orientação', exact: true })).getByText('Bruna Almeida')).toBeTruthy();
    expect(orientationApi.listarVinculos).toHaveBeenCalledWith(ACTIVE_LAB.id, expect.any(AbortSignal));
    expect(orientationApi.listarConvites).not.toHaveBeenCalled();
  });

  it('trata respostas 403 e 404 ao consultar convites', async () => {
    const user = userEvent.setup();
    invitationsApi.listar.mockRejectedValueOnce(apiError(403, 'Acesso negado.'));
    renderPage();
    await openInvitations(user);
    expect((await screen.findByRole('alert')).textContent).toContain('Você não tem permissão para consultar os convites');

    invitationsApi.listar.mockRejectedValueOnce(apiError(404, 'Não encontrado.'));
    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }));
    expect((await screen.findByRole('alert')).textContent).toContain('Este laboratório não foi encontrado');
  });

  it('lista, cria e renomeia laboratório usando somente o campo nome', async () => {
    const user = userEvent.setup();
    const created = { ...ACTIVE_LAB, id: 'b61a3d4f-1f26-41dc-b1e7-8e69b8d75d90', nome: 'Citogenética vegetal' };
    const renamed = { ...created, nome: 'Citogenética de plantas' };
    api.listarMeus.mockResolvedValueOnce([]);
    api.criar.mockResolvedValueOnce(created);
    api.buscar.mockResolvedValue(created);
    api.atualizarNome.mockResolvedValueOnce(renamed);

    renderPage();
    await screen.findByRole('heading', { name: 'Nenhum laboratório ainda' });
    await user.click(screen.getByRole('button', { name: 'Criar laboratório' }));
    await user.type(screen.getByLabelText('Nome do laboratório'), 'Citogenética vegetal');
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Criar laboratório' }));
    await screen.findByRole('heading', { name: 'Citogenética vegetal' });

    expect(api.criar).toHaveBeenCalledWith('Citogenética vegetal');
    expect(await screen.findByText(/Membro e Administrador/)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Configurações' }));
    const name = screen.getByLabelText('Nome do laboratório');
    await user.clear(name);
    await user.type(name, 'Citogenética de plantas');
    await user.click(screen.getByRole('button', { name: 'Salvar nome' }));

    expect(api.atualizarNome).toHaveBeenCalledWith(created.id, 'Citogenética de plantas');
    expect(await screen.findByText('Nome do laboratório atualizado.')).toBeTruthy();
  });

  it('mantém papéis combináveis, MEMBRO obrigatório e envia versaoEsperada', async () => {
    const user = userEvent.setup();
    renderPage();
    await openLaboratoryManagement(user);
    await user.click(screen.getByRole('button', { name: 'Alterar papéis de Bruna Almeida' }));

    const memberRole = screen.getByRole('checkbox', { name: /Membro/ });
    expect((memberRole as HTMLInputElement).disabled).toBe(true);
    expect((memberRole as HTMLInputElement).checked).toBe(true);
    await user.click(screen.getByRole('checkbox', { name: /Orientador/ }));
    await user.click(screen.getByRole('checkbox', { name: /Administrador/ }));
    await user.click(screen.getByRole('button', { name: 'Salvar papéis' }));

    await waitFor(() => expect(api.alterarPapeis).toHaveBeenCalledWith(
      ACTIVE_LAB.id,
      OTHER.id,
      OTHER.versao,
      ['MEMBRO', 'ADMIN'],
    ));
    expect(await screen.findByText('Papéis de Bruna Almeida atualizados.')).toBeTruthy();
  });

  it('recarrega membros e avisa quando a versão da participação ficou obsoleta', async () => {
    const user = userEvent.setup();
    const latestOther = { ...OTHER, papeis: ['MEMBRO', 'ADMIN'] as const, versao: 5 };
    let currentMembers: ParticipacaoResponseDTO[] = [SELF, OTHER];
    api.listarParticipacoes.mockImplementation(async () => currentMembers);
    api.alterarPapeis.mockRejectedValueOnce(apiError(409));
    renderPage();
    await openLaboratoryManagement(user);
    currentMembers = [SELF, latestOther as ParticipacaoResponseDTO];
    await user.click(screen.getByRole('button', { name: 'Alterar papéis de Bruna Almeida' }));
    await user.click(screen.getByRole('checkbox', { name: /Administrador/ }));
    await user.click(screen.getByRole('button', { name: 'Salvar papéis' }));

    expect(await screen.findByText(/papéis foram alterados por outra pessoa/)).toBeTruthy();
    const brunaRow = screen.getByText('Bruna Almeida').closest('tr');
    expect(brunaRow?.querySelector('.role-chip')?.textContent).toBe('Membro');
    expect(brunaRow?.querySelectorAll('.role-chip').length).toBe(2);
    expect(api.listarParticipacoes).toHaveBeenCalledTimes(2);
  });

  it('explica o conflito da remoção do papel Orientador com vínculo ativo', async () => {
    const user = userEvent.setup();
    api.alterarPapeis.mockRejectedValueOnce(apiError(409));
    renderPage();
    await openLaboratoryManagement(user);
    await user.click(screen.getByRole('button', { name: 'Alterar papéis de Bruna Almeida' }));
    await user.click(screen.getByRole('checkbox', { name: /Orientador/ }));
    await user.click(screen.getByRole('button', { name: 'Salvar papéis' }));

    expect(await screen.findByText(/Não foi possível remover o papel Orientador. Encerre todos os vínculos ativos dessa pessoa/)).toBeTruthy();
  });

  it('explica o conflito do último administrador ao alterar papéis e ao sair', async () => {
    const user = userEvent.setup();
    api.listarParticipacoes.mockResolvedValue([SELF]);
    api.alterarPapeis.mockRejectedValueOnce(apiError(409));
    api.sair.mockRejectedValueOnce(apiError(409));
    renderPage();
    await openLaboratoryManagement(user);
    await user.click(screen.getByRole('button', { name: 'Alterar papéis de Ana Pesquisadora' }));
    await user.click(screen.getByRole('checkbox', { name: /Administrador/ }));
    await user.click(screen.getByRole('button', { name: 'Salvar papéis' }));
    expect(await screen.findByText(/É necessário manter ao menos um Administrador ativo/)).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByRole('button', { name: 'Sair do laboratório' })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Configurações' }));
    await user.click(screen.getByRole('button', { name: 'Sair do laboratório' }));
    const dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Sair do laboratório' }));

    expect(await screen.findByText(/Você é o último Administrador ativo/)).toBeTruthy();
    expect(api.sair).toHaveBeenCalledWith(ACTIVE_LAB.id);
  });

  it('remove uma participação ativa depois de confirmar', async () => {
    const user = userEvent.setup();
    renderPage();
    await openLaboratoryManagement(user);
    await user.click(screen.getByRole('button', { name: 'Remover Bruna Almeida do laboratório' }));
    const dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Remover participação' }));

    await waitFor(() => expect(api.removerParticipacao).toHaveBeenCalledWith(ACTIVE_LAB.id, OTHER.id));
    expect(await screen.findByText('Bruna Almeida foi removido do laboratório.')).toBeTruthy();
    expect(screen.queryByText('bruna@ufla.br')).toBeNull();
  });

  it('explica por que não pode remover o último administrador', async () => {
    const user = userEvent.setup();
    const memberOnlySelf = { ...SELF, papeis: ['MEMBRO'] as const };
    const soleAdmin = { ...OTHER, papeis: ['MEMBRO', 'ADMIN'] as const };
    api.listarParticipacoes.mockResolvedValue([memberOnlySelf as ParticipacaoResponseDTO, soleAdmin as ParticipacaoResponseDTO]);
    api.removerParticipacao.mockRejectedValueOnce(apiError(409));
    renderPage();
    await openLaboratoryManagement(user);
    await user.click(screen.getByRole('button', { name: 'Remover Bruna Almeida do laboratório' }));
    const dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Remover participação' }));

    expect(await screen.findByText(/É necessário manter ao menos um Administrador ativo/)).toBeTruthy();
  });

  it('mostra o motivo de permissão 403 ao renomear', async () => {
    const user = userEvent.setup();
    api.atualizarNome.mockRejectedValueOnce(apiError(403, 'Acesso negado.'));
    renderPage();
    await openLaboratoryManagement(user);
    await user.click(screen.getByRole('button', { name: 'Configurações' }));
    await user.click(screen.getByRole('button', { name: 'Salvar nome' }));

    expect(await screen.findByText(/Somente administradores podem alterar o nome/)).toBeTruthy();
    expect(screen.getByText(/Acesso negado/)).toBeTruthy();
  });

  it('volta à lista com aviso se o laboratório retornar 404', async () => {
    const user = userEvent.setup();
    api.buscar.mockRejectedValueOnce(apiError(404, 'Laboratório não encontrado.'));
    renderPage();
    await screen.findByRole('heading', { name: ACTIVE_LAB.nome });
    await user.click(screen.getByRole('button', { name: 'Gerenciar' }));

    expect((await screen.findByRole('status')).textContent).toContain('Este laboratório não está mais disponível');
    expect(screen.getByRole('heading', { name: 'Meus laboratórios' })).toBeTruthy();
  });

  it('mantém o laboratório desativado em consulta e encaminha as lâminas como somente leitura', async () => {
    const user = userEvent.setup();
    const disabled = { ...ACTIVE_LAB, estado: 'DESATIVADO' as const };
    const renderSlides = vi.fn((_lab, _back, readOnly) => <p>{readOnly ? 'Consulta somente leitura' : 'Edição'}</p>);
    api.listarMeus.mockResolvedValue([disabled]);
    api.buscar.mockResolvedValue(disabled);
    api.listarParticipacoes.mockResolvedValue([SELF]);
    renderPage(renderSlides);
    await screen.findByRole('heading', { name: 'Meus laboratórios' });
    await user.click(screen.getByRole('button', { name: 'Gerenciar' }));
    await screen.findByRole('button', { name: 'Configurações' });
    await user.click(screen.getByRole('button', { name: 'Configurações' }));

    expect(screen.queryByLabelText('Nome do laboratório')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Sair do laboratório' })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Meus laboratórios' }));
    await user.click(screen.getAllByRole('button', { name: 'Abrir lâminas' })[0]);

    expect(await screen.findByText('Consulta somente leitura')).toBeTruthy();
    expect(renderSlides).toHaveBeenCalledWith(disabled, expect.any(Function), true);
  });
});
