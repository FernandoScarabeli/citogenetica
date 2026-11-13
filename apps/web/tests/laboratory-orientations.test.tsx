import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '../src/api/errors';
import type { ConviteOrientacaoResponseDTO, VinculoOrientacaoResponseDTO } from '../src/api/contracts/orientacao';

const api = vi.hoisted(() => ({
  listarVinculos: vi.fn(),
  encerrarVinculo: vi.fn(),
  criarConvite: vi.fn(),
  listarConvites: vi.fn(),
  reenviarConvite: vi.fn(),
  cancelarConvite: vi.fn(),
  aceitarConvite: vi.fn(),
  recusarConvite: vi.fn(),
}));

vi.mock('../src/api/orientacao-api', () => ({ orientacaoApi: api }));

import { LaboratoryOrientations } from '../src/LaboratoryOrientations';

const LINK_OWN: VinculoOrientacaoResponseDTO = {
  id: 'link-1', laboratorioId: 'lab-1', orientadorUsuarioId: 12, nomeOrientador: 'Ana Pesquisadora',
  pesquisadorUsuarioId: 51, nomePesquisador: 'Bruna Almeida', criadoEm: '2026-10-20T12:00:00Z',
};
const LINK_OTHER: VinculoOrientacaoResponseDTO = {
  id: 'link-2', laboratorioId: 'lab-1', orientadorUsuarioId: 20, nomeOrientador: 'Caio Pesquisador',
  pesquisadorUsuarioId: 12, nomePesquisador: 'Ana Pesquisadora', criadoEm: '2026-10-21T12:00:00Z',
};
const PENDING: ConviteOrientacaoResponseDTO = {
  id: 'invite-1', laboratorioId: 'lab-1', emailDestino: 'bruna@ufla.br', estado: 'PENDENTE',
  expiraEm: '2026-11-10T18:30:00Z', criadoEm: '2026-11-03T18:30:00Z',
};

function apiError(status: number, message = 'A operação não pode ser concluída.', retryAfterSeconds?: number) {
  return new ApiError({ status, error: status === 409 ? 'Conflict' : 'Error', message, path: '/api/v1/laboratorios/lab-1/orientacoes' }, retryAfterSeconds);
}

function renderSection({ isOrientador = false, isAdmin = false, laboratorioAtivo = true, userId = 12 } = {}) {
  return render(<LaboratoryOrientations laboratorioId="lab-1" userId={userId} isOrientador={isOrientador} isAdmin={isAdmin} laboratorioAtivo={laboratorioAtivo} />);
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
  api.listarVinculos.mockResolvedValue([LINK_OWN, LINK_OTHER]);
  api.encerrarVinculo.mockResolvedValue(undefined);
  api.listarConvites.mockResolvedValue([PENDING]);
  api.criarConvite.mockResolvedValue({ ...PENDING, emailDestino: 'nova@ufla.br' });
  api.reenviarConvite.mockResolvedValue({ ...PENDING, expiraEm: '2026-11-17T18:30:00Z' });
  api.cancelarConvite.mockResolvedValue(undefined);
});

describe('orientação no laboratório', () => {
  it('lista os vínculos devolvidos pela API com nomes, sem inventar vínculos adicionais', async () => {
    api.listarVinculos.mockResolvedValueOnce([LINK_OWN]);
    renderSection();

    expect(await screen.findByText('Ana Pesquisadora')).toBeTruthy();
    expect(screen.getByText('Bruna Almeida')).toBeTruthy();
    expect(screen.queryByText('Caio Pesquisador')).toBeNull();
    expect(screen.getByText(/Desde 20 de out. de 2026/)).toBeTruthy();
  });

  it('só exibe encerrar para o orientador do vínculo ou um administrador', async () => {
    const orientadorPage = renderSection({ isOrientador: true });
    expect((await screen.findAllByRole('button', { name: /Encerrar orientação entre/ }))).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Encerrar orientação entre Ana Pesquisadora e Bruna Almeida' })).toBeTruthy();
    orientadorPage.unmount();

    const adminPage = renderSection({ isAdmin: true });
    expect(await screen.findAllByRole('button', { name: /Encerrar orientação entre/ })).toHaveLength(2);
    adminPage.unmount();

    renderSection({ userId: 51 });
    await screen.findByText('Bruna Almeida');
    expect(screen.queryByRole('button', { name: /Encerrar orientação entre/ })).toBeNull();
  });

  it('encerra o vínculo somente após confirmação acessível', async () => {
    const user = userEvent.setup();
    renderSection({ isOrientador: true });
    await user.click(await screen.findByRole('button', { name: 'Encerrar orientação entre Ana Pesquisadora e Bruna Almeida' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Encerrar orientação?' })).toBeTruthy();
    expect(document.activeElement).toBe(within(dialog).getByRole('button', { name: 'Manter orientação' }));
    await user.click(within(dialog).getByRole('button', { name: 'Encerrar orientação' }));

    await waitFor(() => expect(api.encerrarVinculo).toHaveBeenCalledWith('lab-1', LINK_OWN.id));
    expect(await screen.findByText(/Orientação entre Ana Pesquisadora e Bruna Almeida encerrada/)).toBeTruthy();
    expect(screen.queryByText('Bruna Almeida')).toBeNull();
  });

  it('mostra somente consulta em laboratório desativado e não lista convites', async () => {
    renderSection({ isOrientador: true, isAdmin: true, laboratorioAtivo: false });
    expect(await screen.findByText(/disponíveis somente para consulta/)).toBeTruthy();
    expect(screen.getByText('Bruna Almeida')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Encerrar orientação/ })).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Convidar para orientação' })).toBeNull();
    expect(api.listarConvites).not.toHaveBeenCalled();
  });

  it('esconde convites de quem não tem papel Orientador', async () => {
    renderSection();
    await screen.findByText('Bruna Almeida');
    expect(screen.queryByRole('heading', { name: 'Convidar para orientação' })).toBeNull();
    expect(api.listarConvites).not.toHaveBeenCalled();
  });

  it('convida por e-mail e explica a orientação e a participação no laboratório', async () => {
    const user = userEvent.setup();
    renderSection({ isOrientador: true });
    expect(await screen.findByText('bruna@ufla.br')).toBeTruthy();
    expect(screen.getByText(/passará a ser orientada por você quando aceitar/)).toBeTruthy();
    expect(screen.getByText(/também entrará como Membro/)).toBeTruthy();
    await user.type(screen.getByLabelText('E-mail da pessoa'), 'nova@ufla.br');
    await user.click(screen.getByRole('button', { name: 'Convidar para orientação' }));

    await waitFor(() => expect(api.criarConvite).toHaveBeenCalledWith('lab-1', 'nova@ufla.br'));
    expect(await screen.findByText('Convite de orientação enviado para nova@ufla.br.')).toBeTruthy();
  });

  it.each([
    ['par já ativo', 'Este pesquisador já possui vínculo ativo com você neste laboratório.', 'já possui uma orientação ativa'],
    ['convite pendente', 'Já existe convite de orientação pendente para este endereço; use o reenvio.', 'Use Reenviar na lista'],
    ['auto-orientação', 'Você não pode enviar um convite de orientação para si mesmo.', 'não pode convidar a si mesmo'],
  ])('explica conflito 409 de %s', async (_scenario, backendMessage, expectedMessage) => {
    const user = userEvent.setup();
    api.criarConvite.mockRejectedValueOnce(apiError(409, backendMessage));
    renderSection({ isOrientador: true });
    await screen.findByText('bruna@ufla.br');
    await user.type(screen.getByLabelText('E-mail da pessoa'), 'destino@ufla.br');
    await user.click(screen.getByRole('button', { name: 'Convidar para orientação' }));
    await waitFor(() => expect(api.criarConvite).toHaveBeenCalledWith('lab-1', 'destino@ufla.br'));

    expect((await screen.findByRole('alert')).textContent).toContain(expectedMessage);
  });

  it('mostra a contagem de Retry-After e bloqueia reenvio durante o prazo', async () => {
    const user = userEvent.setup();
    api.reenviarConvite.mockRejectedValueOnce(apiError(429, 'Limite de envios atingido.', 8));
    renderSection({ isOrientador: true });
    await screen.findByText('bruna@ufla.br');
    await user.click(screen.getByRole('button', { name: 'Reenviar' }));

    expect((await screen.findByRole('alert')).textContent).toContain('Tente novamente em 8 segundos.');
    expect((screen.getByRole('button', { name: 'Aguarde 8 s' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('trata 403 e 404 sem expor uma ação permitida', async () => {
    const user = userEvent.setup();
    api.criarConvite.mockRejectedValueOnce(apiError(403, 'Acesso negado.'));
    renderSection({ isOrientador: true });
    await screen.findByText('bruna@ufla.br');
    await user.type(screen.getByLabelText('E-mail da pessoa'), 'destino@ufla.br');
    await user.click(screen.getByRole('button', { name: 'Convidar para orientação' }));
    expect((await screen.findByRole('alert')).textContent).toContain('Você precisa ter o papel Orientador');

    api.listarConvites.mockRejectedValueOnce(apiError(404, 'Não encontrado.'));
    fireEvent.click(screen.getByRole('button', { name: 'Atualizar' }));
    expect((await screen.findByRole('alert')).textContent).toContain('O laboratório, sua participação ou o convite não está mais disponível');
  });

  it('lista os próprios convites, reenvia e confirma o cancelamento', async () => {
    const user = userEvent.setup();
    renderSection({ isOrientador: true });
    expect(await screen.findByText('bruna@ufla.br')).toBeTruthy();
    expect(screen.getByText(/Convite enviado em 3 de nov. de 2026/)).toBeTruthy();
    expect(screen.getByText(/Válido até 10 de nov. de 2026/)).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Reenviar' }));
    await waitFor(() => expect(api.reenviarConvite).toHaveBeenCalledWith('lab-1', PENDING.id));
    expect(await screen.findByText('Convite de orientação reenviado para bruna@ufla.br.')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Cancelar convite de orientação para bruna@ufla.br' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Cancelar convite de orientação?' })).toBeTruthy();
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar convite' }));
    await waitFor(() => expect(api.cancelarConvite).toHaveBeenCalledWith('lab-1', PENDING.id));
    expect(await screen.findByText('Não há convites de orientação pendentes.')).toBeTruthy();
  });
});
