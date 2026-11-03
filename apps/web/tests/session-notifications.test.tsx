import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { SessionProvider, useSession } from '../src/app/SessionProvider';
import type { UsuarioResponseDTO } from '../src/api/contracts/usuario';
import type { HttpClient } from '../src/api/http-client';

const user: UsuarioResponseDTO = {
  id: 42,
  version: 0,
  createdDate: '2026-11-03T12:00:00',
  lastModifiedDate: null,
  nome: 'Ana Pesquisadora',
  email: 'ana@ufla.br',
  status: 'ATIVO',
  role: { id: 1, version: 0, createdDate: '2026-11-03T12:00:00', lastModifiedDate: null, nome: 'USER' },
};

function SessionActions() {
  const session = useSession();
  return <button type="button" onClick={() => void session.logout()}>Sair da sessão</button>;
}

describe('ciclo de vida das notificações na sessão', () => {
  it('abre o SSE uma vez para a sessão e fecha antes de solicitar logout', async () => {
    const order: string[] = [];
    let closeStream = () => undefined;
    const notifications = {
      startRealtime: vi.fn(() => {
        order.push('sse-aberto');
        let closed = false;
        closeStream = () => {
          if (closed) return;
          closed = true;
          order.push('sse-fechado');
        };
        return closeStream;
      }),
      stopRealtime: vi.fn(() => {
        order.push('parada-explícita');
        closeStream();
      }),
    };
    const auth = { logout: vi.fn(async () => { order.push('logout-api'); }) };
    const usuario = { getMe: vi.fn(async () => user) };
    const client = { onUnauthorized: vi.fn(() => () => undefined) } as unknown as HttpClient;

    render(<MemoryRouter initialEntries={['/app']}>
      <SessionProvider
        auth={auth as never}
        usuario={usuario as never}
        client={client}
        notifications={notifications}
      ><SessionActions/></SessionProvider>
    </MemoryRouter>);

    await waitFor(() => expect(notifications.startRealtime).toHaveBeenCalledWith(42));
    expect(notifications.startRealtime).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: 'Sair da sessão' }));
    await waitFor(() => expect(auth.logout).toHaveBeenCalledTimes(1));
    expect(order.indexOf('parada-explícita')).toBeLessThan(order.indexOf('logout-api'));
    expect(order).toContain('sse-fechado');
  });
});
