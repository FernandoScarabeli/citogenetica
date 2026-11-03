import { describe, expect, it, vi } from 'vitest';
import { createOrientacaoApi } from '../src/api/orientacao-api';
import type { HttpClient } from '../src/api/http-client';

describe('orientacaoApi', () => {
  it('consulta vínculos visíveis e convites pendentes nos endpoints corretos', async () => {
    const request = vi.fn().mockResolvedValueOnce([]).mockResolvedValueOnce([]);
    const api = createOrientacaoApi({ request } as unknown as Pick<HttpClient, 'request'>);

    await api.listarVinculos('lab-1');
    await api.listarConvites('lab-1');

    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/laboratorios/lab-1/orientacoes', {});
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/laboratorios/lab-1/orientacoes/convites', {});
  });

  it('envia, reenvia, cancela e encerra usando os métodos do contrato', async () => {
    const request = vi.fn().mockResolvedValue(undefined);
    const api = createOrientacaoApi({ request } as unknown as Pick<HttpClient, 'request'>);

    await api.criarConvite('lab-1', 'pesquisadora@ufla.br');
    await api.reenviarConvite('lab-1', 'invite-1');
    await api.cancelarConvite('lab-1', 'invite-1');
    await api.encerrarVinculo('lab-1', 'link-1');

    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/laboratorios/lab-1/orientacoes/convites', {
      method: 'POST', json: { email: 'pesquisadora@ufla.br' },
    });
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/laboratorios/lab-1/orientacoes/convites/invite-1/reenviar', { method: 'POST' });
    expect(request).toHaveBeenNthCalledWith(3, '/api/v1/laboratorios/lab-1/orientacoes/convites/invite-1', { method: 'DELETE' });
    expect(request).toHaveBeenNthCalledWith(4, '/api/v1/laboratorios/lab-1/orientacoes/link-1', { method: 'DELETE' });
  });

  it('consome aceite e recusa enviando o token somente no corpo', async () => {
    const request = vi.fn().mockResolvedValue(undefined);
    const api = createOrientacaoApi({ request } as unknown as Pick<HttpClient, 'request'>);

    await api.aceitarConvite('token+seguro');
    await api.recusarConvite('token+seguro');

    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/orientacoes/convites/aceitar', {
      method: 'POST', json: { token: 'token+seguro' },
    });
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/orientacoes/convites/recusar', {
      method: 'POST', json: { token: 'token+seguro' },
    });
  });
});
