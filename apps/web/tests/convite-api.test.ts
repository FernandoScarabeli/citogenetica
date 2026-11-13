import { describe, expect, it, vi } from 'vitest';
import { createConviteApi } from '../src/api/convite-api';

function setup() {
  const request = vi.fn();
  const api = createConviteApi({ request } as never);
  return { api, request };
}

describe('cliente de convites', () => {
  it('cria e lista convites usando os campos e caminhos do contrato', async () => {
    const { api, request } = setup();
    request.mockResolvedValueOnce({ id: 'convite-1' }).mockResolvedValueOnce([]);

    await api.criar('lab-uuid', 'ana@ufla.br', ['MEMBRO', 'ADMIN']);
    await api.listar('lab-uuid');

    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/laboratorios/lab-uuid/convites', {
      method: 'POST', json: { email: 'ana@ufla.br', papeis: ['MEMBRO', 'ADMIN'] },
    });
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/laboratorios/lab-uuid/convites', {});
  });

  it('reenvia e revoga convite pelos métodos definidos na API', async () => {
    const { api, request } = setup();
    request.mockResolvedValue(undefined);

    await api.reenviar('lab-uuid', 'convite-uuid');
    await api.revogar('lab-uuid', 'convite-uuid');

    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/laboratorios/lab-uuid/convites/convite-uuid/reenviar', { method: 'POST' });
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/laboratorios/lab-uuid/convites/convite-uuid', { method: 'DELETE' });
  });

  it('aceita e recusa enviando apenas o token no corpo', async () => {
    const { api, request } = setup();
    request.mockResolvedValue(undefined);

    await api.aceitar('token-seguro');
    await api.recusar('token-seguro');

    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/convites/aceitar', { method: 'POST', json: { token: 'token-seguro' } });
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/convites/recusar', { method: 'POST', json: { token: 'token-seguro' } });
  });
});
