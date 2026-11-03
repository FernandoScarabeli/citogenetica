import { describe, expect, it, vi } from 'vitest';
import { createLaboratorioApi } from '../src/api/laboratorio-api';

function setup() {
  const request = vi.fn();
  const api = createLaboratorioApi({ request } as never);
  return { api, request };
}

describe('cliente de laboratórios', () => {
  it('lista laboratórios e busca o detalhe pela API real', async () => {
    const { api, request } = setup();
    request.mockResolvedValueOnce([]).mockResolvedValueOnce({ id: 'lab-uuid' });

    await api.listarMeus();
    await api.buscar('lab-uuid');

    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/laboratorios', {});
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/laboratorios/lab-uuid', {});
  });

  it('cria laboratório enviando somente o nome', async () => {
    const { api, request } = setup();
    request.mockResolvedValueOnce({ id: 'lab-uuid' });

    await api.criar('Citogenética vegetal');

    expect(request).toHaveBeenCalledWith('/api/v1/laboratorios', {
      method: 'POST',
      json: { nome: 'Citogenética vegetal' },
    });
  });

  it('renomeia por PATCH com somente o nome', async () => {
    const { api, request } = setup();
    request.mockResolvedValueOnce({ id: 'lab-uuid', nome: 'Nome atualizado' });

    await api.atualizarNome('lab-uuid', 'Nome atualizado');

    expect(request).toHaveBeenCalledWith('/api/v1/laboratorios/lab-uuid', {
      method: 'PATCH',
      json: { nome: 'Nome atualizado' },
    });
  });

  it('lista participações e altera papéis com a versão esperada', async () => {
    const { api, request } = setup();
    request.mockResolvedValueOnce([]).mockResolvedValueOnce({ id: 'participacao-uuid' });

    await api.listarParticipacoes('lab-uuid');
    await api.alterarPapeis('lab-uuid', 'participacao-uuid', 7, ['MEMBRO', 'ADMIN', 'ORIENTADOR']);

    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/laboratorios/lab-uuid/participacoes', {});
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/laboratorios/lab-uuid/participacoes/participacao-uuid/papeis', {
      method: 'PATCH',
      json: { versaoEsperada: 7, papeis: ['MEMBRO', 'ADMIN', 'ORIENTADOR'] },
    });
  });

  it('remove uma participação e encerra a própria participação nos endpoints corretos', async () => {
    const { api, request } = setup();
    request.mockResolvedValue(undefined);

    await api.removerParticipacao('lab-uuid', 'participacao-uuid');
    await api.sair('lab-uuid');

    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/laboratorios/lab-uuid/participacoes/participacao-uuid', { method: 'DELETE' });
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/laboratorios/lab-uuid/participacoes/me', { method: 'DELETE' });
  });
});
