import { describe, expect, it, vi } from 'vitest';
import { createContagemApi } from '../src/api/contagem-api';

function setup() {
  const request = vi.fn().mockResolvedValue({});
  return { api: createContagemApi({ request } as never), request };
}

const SIGNAL = new AbortController().signal;

describe('cliente da contagem', () => {
  it('lista protocolos e busca uma versão', async () => {
    const { api, request } = setup();
    await api.listarProtocolos(SIGNAL);
    await api.buscarVersaoProtocolo('v 1');
    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/protocolos', { signal: SIGNAL });
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/protocolos/versoes/v%201', {});
  });

  it('lista lâminas avulsas ou de um laboratório, com página e tamanho', async () => {
    const { api, request } = setup();
    await api.listarLaminas({ page: 2, size: 100, signal: SIGNAL });
    await api.listarLaminas({ laboratorioId: 'lab-1' });
    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/laminas?page=2&size=100', { signal: SIGNAL });
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/laminas?page=0&size=10&laboratorioId=lab-1', {});
  });

  it('cria, busca, atualiza e exclui lâmina', async () => {
    const { api, request } = setup();
    const nova = { id: 'l1', protocoloVersaoId: 'v1', nome: 'L1', meta: 1000 };
    await api.criarLamina(nova);
    await api.buscarLamina('l1');
    const patch = { versaoEsperada: 3, nome: 'L1', observacoes: 'x', meta: null };
    await api.atualizarLamina('l1', patch);
    await api.excluirLamina('l1', 4);
    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/laminas', { method: 'POST', json: nova });
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/laminas/l1', {});
    expect(request).toHaveBeenNthCalledWith(3, '/api/v1/laminas/l1', { method: 'PATCH', json: patch });
    expect(request).toHaveBeenNthCalledWith(4, '/api/v1/laminas/l1?versaoEsperada=4', { method: 'DELETE' });
  });

  it('cria, lista, edita e desfaz registros e busca o resumo', async () => {
    const { api, request } = setup();
    const registro = { id: 'r1', faseId: 'f1', status: 'ANORMAL' as const, tipoIds: ['t1', 't2'], quantidade: 1, horaAparelho: '2026-11-08T12:00:00Z', fusoAparelho: 'America/Sao_Paulo' };
    await api.criarRegistro('l1', registro, SIGNAL);
    await api.listarRegistros('l1', { page: 1, size: 20 });
    const edicao = { versaoEsperada: 1, faseId: 'f2', status: 'NORMAL' as const, quantidade: 5 };
    await api.atualizarRegistro('l1', 'r1', edicao);
    const desfazer = { id: 'd1', faseId: 'f1', status: 'ANORMAL' as const, tipoIds: ['t1'], quantidade: 2 };
    await api.desfazerRegistros('l1', desfazer);
    await api.buscarResumo('l1');
    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/laminas/l1/registros', { method: 'POST', json: registro, signal: SIGNAL });
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/laminas/l1/registros?page=1&size=20', {});
    expect(request).toHaveBeenNthCalledWith(3, '/api/v1/laminas/l1/registros/r1', { method: 'PATCH', json: edicao });
    expect(request).toHaveBeenNthCalledWith(4, '/api/v1/laminas/l1/registros/desfazer', { method: 'POST', json: desfazer });
    expect(request).toHaveBeenNthCalledWith(5, '/api/v1/laminas/l1/resumo', {});
  });

  it('lista e cria tipos personalizados', async () => {
    const { api, request } = setup();
    await api.listarTiposPersonalizados('v1');
    await api.listarTiposPersonalizados('v1', 'lab-1');
    const tipo = { id: 't1', faseId: 'f1', laboratorioId: 'lab-1', nome: 'Ponte dupla' };
    await api.criarTipoPersonalizado(tipo);
    expect(request).toHaveBeenNthCalledWith(1, '/api/v1/tipos-anormalidade/personalizados?versaoId=v1', {});
    expect(request).toHaveBeenNthCalledWith(2, '/api/v1/tipos-anormalidade/personalizados?versaoId=v1&laboratorioId=lab-1', {});
    expect(request).toHaveBeenNthCalledWith(3, '/api/v1/tipos-anormalidade/personalizados', { method: 'POST', json: tipo });
  });
});
