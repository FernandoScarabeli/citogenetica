import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SlideComments, SlidePhotos } from '../src/SlideCollaboration';
import type { ColaboracaoApi, ComentarioLaminaDTO, FotoLaminaDTO } from '../src/api/colaboracao-api';
import { ApiError } from '../src/api/errors';

const FOTO: FotoLaminaDTO = {
  id: 'foto-1', legenda: 'Metáfase com ponte', tamanhoBytes: 1200, contentType: 'image/jpeg',
  enviadaPorUsuarioId: 12, enviadaPorNome: 'Ana Pesquisadora', enviadaEm: '2026-11-13T12:00:00Z',
};
const COMENTARIO: ComentarioLaminaDTO = {
  id: 'c-1', autorUsuarioId: 30, autorNome: 'Prof. Orientador', texto: 'Confira a anáfase.', criadoEm: '2026-11-13T12:10:00Z',
};

function apiFalsa() {
  return {
    listarFotos: vi.fn(async () => [FOTO]),
    enviarFoto: vi.fn(async (_lamina: string, _arquivo: File, legenda: string) => ({ ...FOTO, id: 'foto-2', legenda: legenda || null })),
    apagarFoto: vi.fn(async () => undefined),
    listarComentarios: vi.fn(async () => [COMENTARIO]),
    comentar: vi.fn(async (_lamina: string, texto: string) => ({ ...COMENTARIO, id: 'c-2', autorUsuarioId: 12, autorNome: 'Ana Pesquisadora', texto })),
    apagarComentario: vi.fn(async () => undefined),
  };
}

let api: ReturnType<typeof apiFalsa>;
beforeEach(() => {
  api = apiFalsa();
  URL.createObjectURL = vi.fn(() => 'blob:previa');
  URL.revokeObjectURL = vi.fn();
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value(this: HTMLDialogElement) { this.setAttribute('open', ''); } });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value(this: HTMLDialogElement) { this.removeAttribute('open'); } });
});

describe('fotos da lâmina', () => {
  it('mostra a galeria, envia foto com legenda e apaga pela foto aberta', async () => {
    const user = userEvent.setup();
    render(<SlidePhotos laminaId="lamina-1" canEdit api={api as unknown as ColaboracaoApi} />);
    const galeria = await screen.findByRole('list', { name: 'Fotos da lâmina' });
    expect(within(galeria).getByRole('button', { name: 'Abrir foto: Metáfase com ponte' }).querySelector('img')?.getAttribute('src'))
      .toBe('/api/v1/laminas/lamina-1/fotos/foto-1/arquivo');

    await user.upload(screen.getByLabelText('Escolher foto da lâmina'), new File([new Uint8Array([0xff, 0xd8, 0xff])], 'm.jpg', { type: 'image/jpeg' }));
    await user.type(screen.getByRole('textbox', { name: /Legenda/ }), 'Prófase');
    await user.click(screen.getByRole('button', { name: 'Enviar foto' }));
    await waitFor(() => expect(within(galeria).getAllByRole('listitem')).toHaveLength(2));
    expect(api.enviarFoto).toHaveBeenCalledWith('lamina-1', expect.any(File), 'Prófase');

    await user.click(within(galeria).getByRole('button', { name: 'Abrir foto: Metáfase com ponte' }));
    expect(screen.getByText(/Enviada por Ana Pesquisadora/)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: /Apagar foto/ }));
    await waitFor(() => expect(within(galeria).getAllByRole('listitem')).toHaveLength(1));
    expect(api.apagarFoto).toHaveBeenCalledWith('lamina-1', 'foto-1');
  });

  it('quem só vê não tem botão de enviar nem de apagar, e arquivo errado é recusado antes de enviar', async () => {
    const user = userEvent.setup({ applyAccept: false });
    const { unmount } = render(<SlidePhotos laminaId="lamina-1" canEdit={false} api={api as unknown as ColaboracaoApi} />);
    await user.click(await screen.findByRole('button', { name: /Abrir foto/ }));
    expect(screen.queryByRole('button', { name: /Apagar foto/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Adicionar foto/ })).toBeNull();
    unmount();

    render(<SlidePhotos laminaId="lamina-1" canEdit api={api as unknown as ColaboracaoApi} />);
    await screen.findByRole('list', { name: 'Fotos da lâmina' });
    await user.upload(screen.getByLabelText('Escolher foto da lâmina'), new File(['x'], 'a.gif', { type: 'image/gif' }));
    expect(screen.getByRole('alert').textContent).toBe('Escolha uma imagem JPG, PNG ou WebP de até 10 MB.');
    expect(api.enviarFoto).not.toHaveBeenCalled();
  });
});

describe('comentários da lâmina', () => {
  it('lista, comenta e apaga só o próprio comentário', async () => {
    const user = userEvent.setup();
    render(<SlideComments laminaId="lamina-1" usuarioId={12} canComment api={api as unknown as ColaboracaoApi} />);
    const lista = await screen.findByRole('list', { name: 'Comentários da lâmina' });
    expect(within(lista).getByText('Confira a anáfase.')).toBeTruthy();
    expect(within(lista).queryByRole('button', { name: /Apagar/ })).toBeNull();

    await user.type(screen.getByRole('textbox', { name: 'Escrever comentário' }), 'Era uma ponte.');
    await user.click(screen.getByRole('button', { name: 'Comentar' }));
    expect(await within(lista).findByText('Era uma ponte.')).toBeTruthy();
    expect((screen.getByRole('textbox', { name: 'Escrever comentário' }) as HTMLTextAreaElement).value).toBe('');

    await user.click(within(lista).getByRole('button', { name: /Apagar/ }));
    await waitFor(() => expect(within(lista).queryByText('Era uma ponte.')).toBeNull());
    expect(api.apagarComentario).toHaveBeenCalledWith('lamina-1', 'c-2');
  });

  it('laboratório desativado mostra os comentários sem caixa de escrever e erro do servidor aparece', async () => {
    const user = userEvent.setup();
    const { unmount } = render(<SlideComments laminaId="lamina-1" usuarioId={12} canComment={false} api={api as unknown as ColaboracaoApi} />);
    await screen.findByText('Confira a anáfase.');
    expect(screen.queryByRole('textbox', { name: 'Escrever comentário' })).toBeNull();
    unmount();

    api.comentar.mockRejectedValueOnce(new ApiError({ status: 403, error: 'Forbidden', message: 'Acesso negado.', path: '/x' }));
    render(<SlideComments laminaId="lamina-1" usuarioId={12} canComment api={api as unknown as ColaboracaoApi} />);
    await user.type(await screen.findByRole('textbox', { name: 'Escrever comentário' }), 'Oi');
    await user.click(screen.getByRole('button', { name: 'Comentar' }));
    expect((await screen.findByRole('alert')).textContent).toBe('Acesso negado.');
  });
});
