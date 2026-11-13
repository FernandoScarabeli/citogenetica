import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { EVENTO_CONVITE_ACEITO, NotificationCenter, type ResponderConvite } from '../src/NotificationCenter';
import { ApiError } from '../src/api/errors';
import type { NotificacaoResponseDTO } from '../src/api/contracts/notificacao';
import type { createNotificacaoApi } from '../src/api/notificacao-api';

function notification(overrides: Partial<NotificacaoResponseDTO> = {}): NotificacaoResponseDTO {
  return {
    id: 3,
    tipo: 'INFO',
    titulo: 'Atualização do laboratório',
    mensagem: 'A equipe atualizou as informações do laboratório.',
    dados: null,
    lida: false,
    dataCriacao: '2026-11-03T12:30:00',
    ...overrides,
  };
}

function apiFixture(options: {
  count?: number;
  pages?: Array<NotificacaoResponseDTO[]>;
  countError?: boolean;
  listError?: boolean;
} = {}) {
  let changed: (() => void) | undefined;
  let count = options.count ?? 0;
  const items = (options.pages ?? [[]]).flat().map(item => ({ ...item }));
  const listar = vi.fn(async ({ page = 0 }: { page?: number } = {}) => {
    if (options.listError) throw new Error('Falha de rede');
    const content = options.pages?.[page] ?? [];
    return { content, number: page, totalPages: options.pages?.length ?? 1, last: page >= (options.pages?.length ?? 1) - 1 };
  });
  const api = {
    listar,
    contarNaoLidas: vi.fn(async () => {
      if (options.countError) throw new Error('Falha de rede');
      return { count };
    }),
    marcarLida: vi.fn(async (id: number) => {
      const item = items.find(entry => entry.id === id);
      if (!item) throw new Error('Notificação não encontrada');
      item.lida = true;
      count = Math.max(0, count - 1);
      return { ...item };
    }),
    marcarTodasLidas: vi.fn(async () => {
      items.forEach(item => { item.lida = true; });
      count = 0;
      return { marcadas: options.count ?? 0 };
    }),
    subscribeChanges: vi.fn((listener: () => void) => {
      changed = listener;
      return () => { changed = undefined; };
    }),
  };
  return {
    api: api as unknown as ReturnType<typeof createNotificacaoApi>,
    changed: () => changed?.(),
    setCount: (value: number) => { count = value; },
    listar,
    items,
  };
}

function renderCenter(api: ReturnType<typeof createNotificacaoApi>, responderConvite?: ResponderConvite) {
  return render(<div className="labs-app"><NotificationCenter api={api} responderConvite={responderConvite} /></div>);
}

describe('NotificationCenter', () => {
  it('expõe a contagem no nome acessível do sino e abre a lista vazia', async () => {
    const { api } = apiFixture({ count: 0 });
    renderCenter(api);

    const bell = screen.getByRole('button', { name: 'Notificações, 0 não lidas' });
    expect(bell.getAttribute('aria-expanded')).toBe('false');
    await userEvent.setup().click(bell);

    expect(await screen.findByRole('heading', { name: 'Notificações' })).toBeTruthy();
    expect(await screen.findByText('Nenhuma notificação por enquanto.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Notificações, 0 não lidas' }).getAttribute('aria-expanded')).toBe('true');
  });

  it('carrega páginas, marca uma notificação e marca todas como lidas', async () => {
    const first = notification({ id: 1, titulo: 'Convite para laboratório', mensagem: 'Você recebeu um convite para o laboratório “Raízes”.', dados: { conviteId: 'convite-1' } });
    const second = notification({ id: 2, titulo: 'Convite de orientação', mensagem: 'Você recebeu um convite de orientação no laboratório “Raízes”.' });
    const third = notification({ id: 4, titulo: 'Resultado disponível', lida: true });
    const fixture = apiFixture({ count: 2, pages: [[first, second], [third]] });
    renderCenter(fixture.api);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Notificações, 2 não lidas' }));

    // Com o id do convite dá para responder ali; sem ele, sobra o caminho do e-mail.
    expect(await screen.findByRole('button', { name: 'Aceitar' })).toBeTruthy();
    expect(screen.getAllByText('Para aceitar, use o link recebido por e-mail.')).toHaveLength(1);
    expect(screen.getByText(/Convite para laboratório/)).toBeTruthy();
    expect(screen.queryByText('convite-1')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Carregar mais' }));
    expect(await screen.findByRole('heading', { name: 'Resultado disponível' })).toBeTruthy();
    expect(fixture.listar).toHaveBeenLastCalledWith({ page: 1, size: 20 });

    const firstItem = screen.getByRole('heading', { name: 'Convite para laboratório' }).closest('li');
    expect(firstItem).not.toBeNull();
    await user.click(within(firstItem as HTMLElement).getByRole('button', { name: 'Marcar como lida' }));
    await waitFor(() => expect(fixture.api.marcarLida).toHaveBeenCalledWith(1));
    expect(screen.getByRole('button', { name: 'Notificações, 1 não lida' })).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Marcar todas como lidas' }));
    await waitFor(() => expect(fixture.api.marcarTodasLidas).toHaveBeenCalledTimes(1));
    expect(screen.getByRole('button', { name: 'Notificações, 0 não lidas' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Marcar todas como lidas' })).toBeNull();
  });

  it('aceita o convite pelo sino, marca como lida e avisa a lista de laboratórios', async () => {
    const convite = notification({ id: 1, titulo: 'Convite para laboratório', mensagem: 'Você recebeu um convite para o laboratório “Raízes”.', dados: { conviteId: 'convite-1', convite: 'LABORATORIO' } });
    const fixture = apiFixture({ count: 1, pages: [[convite]] });
    const responder = vi.fn<ResponderConvite>(async () => undefined);
    const avisado = vi.fn();
    window.addEventListener(EVENTO_CONVITE_ACEITO, avisado);
    try {
      renderCenter(fixture.api, responder);
      const user = userEvent.setup();
      await user.click(await screen.findByRole('button', { name: 'Notificações, 1 não lida' }));
      await user.click(await screen.findByRole('button', { name: 'Aceitar' }));

      expect(await screen.findByText('Convite aceito.')).toBeTruthy();
      expect(responder).toHaveBeenCalledWith('LABORATORIO', 'convite-1', 'aceitar');
      expect(fixture.api.marcarLida).toHaveBeenCalledWith(1);
      expect(avisado).toHaveBeenCalledTimes(1);
      expect(screen.queryByRole('button', { name: 'Aceitar' })).toBeNull();
      expect(screen.getByRole('button', { name: 'Notificações, 0 não lidas' })).toBeTruthy();
    } finally {
      window.removeEventListener(EVENTO_CONVITE_ACEITO, avisado);
    }
  });

  it('recusa convite de orientação antigo (tipo pelo título) e explica convite já respondido', async () => {
    const orientacao = notification({ id: 5, titulo: 'Convite de orientação', mensagem: 'Você recebeu um convite de orientação.', dados: { conviteId: 'orient-1' } });
    const velho = notification({ id: 6, titulo: 'Convite para laboratório', mensagem: 'Convite antigo.', dados: { conviteId: 'lab-velho' } });
    const fixture = apiFixture({ count: 2, pages: [[orientacao, velho]] });
    const responder = vi.fn<ResponderConvite>(async (_tipo, id) => {
      if (id === 'lab-velho') throw new ApiError({ status: 400, error: 'Bad Request', message: 'Convite inválido ou indisponível.', path: '/api/v1/convites/lab-velho/aceitar' });
    });
    renderCenter(fixture.api, responder);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Notificações, 2 não lidas' }));

    const itemOrientacao = (await screen.findByRole('heading', { name: 'Convite de orientação' })).closest('li') as HTMLElement;
    await user.click(within(itemOrientacao).getByRole('button', { name: 'Recusar' }));
    expect(await within(itemOrientacao).findByText('Convite recusado.')).toBeTruthy();
    expect(responder).toHaveBeenCalledWith('ORIENTACAO', 'orient-1', 'recusar');

    const itemVelho = screen.getByRole('heading', { name: 'Convite para laboratório' }).closest('li') as HTMLElement;
    await user.click(within(itemVelho).getByRole('button', { name: 'Aceitar' }));
    expect((await screen.findByRole('alert')).textContent).toBe('Este convite já foi respondido, expirou ou foi cancelado.');
    expect(within(itemVelho).getByRole('button', { name: 'Aceitar' })).toBeTruthy();
  });

  it('atualiza a contagem e a primeira página após evento e ao voltar para a aba', async () => {
    const fixture = apiFixture({ count: 0, pages: [[notification({ id: 1 })]] });
    renderCenter(fixture.api);
    await waitFor(() => expect(fixture.api.contarNaoLidas).toHaveBeenCalled());
    await userEvent.setup().click(screen.getByRole('button', { name: 'Notificações, 0 não lidas' }));
    await waitFor(() => expect(fixture.listar).toHaveBeenCalledWith({ page: 0, size: 20 }));

    fixture.setCount(4);
    fixture.changed();
    await waitFor(() => expect(fixture.listar).toHaveBeenCalledTimes(2));
    expect(await screen.findByRole('button', { name: 'Notificações, 4 não lidas' })).toBeTruthy();
    fireEvent.focus(window);
    await waitFor(() => expect(fixture.api.contarNaoLidas).toHaveBeenCalledTimes(4));
  });

  it('mostra estados de erro e oferece nova tentativa', async () => {
    const fixture = apiFixture({ countError: true, listError: true });
    renderCenter(fixture.api);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Notificações/ }));

    expect(await screen.findByText('Não foi possível atualizar a contagem.')).toBeTruthy();
    expect(await screen.findByText('Não foi possível carregar as notificações.')).toBeTruthy();
    expect(screen.getAllByRole('button', { name: 'Tentar novamente' })).toHaveLength(2);
    await user.click(screen.getAllByRole('button', { name: 'Tentar novamente' })[1]);
    await waitFor(() => expect(fixture.listar).toHaveBeenCalledTimes(2));
  });
});
