import { describe, expect, it, vi } from 'vitest';
import { createAdminApi } from '../src/api/admin-api';

function setup() {
  const request = vi.fn();
  return { api: createAdminApi({ request } as never), request };
}

describe('cliente de administração da plataforma', () => {
  it('lista com paginação padrão e sem filtros vazios', async () => {
    const { api, request } = setup();
    request.mockResolvedValue({ content: [] });
    await api.listarLaboratorios();
    expect(request).toHaveBeenCalledWith('/api/v1/admin/laboratorios?page=0&size=10', {});
  });

  it('envia nome, estado, página e sinal de cancelamento', async () => {
    const { api, request } = setup();
    request.mockResolvedValue({ content: [] });
    const controller = new AbortController();
    await api.listarLaboratorios({ nome: '  Citogenética ', estado: 'DESATIVADO', page: 2, size: 20, signal: controller.signal });
    expect(request).toHaveBeenCalledWith(
      '/api/v1/admin/laboratorios?page=2&size=20&nome=Citogen%C3%A9tica&estado=DESATIVADO',
      { signal: controller.signal },
    );
  });

  it('desativa e reativa por PATCH', async () => {
    const { api, request } = setup();
    request.mockResolvedValue(undefined);
    await api.desativarLaboratorio('lab-1');
    await api.reativarLaboratorio('lab-1');
    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/admin/laboratorios/lab-1/desativar', { method: 'PATCH' });
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/admin/laboratorios/lab-1/reativar', { method: 'PATCH' });
  });
});
