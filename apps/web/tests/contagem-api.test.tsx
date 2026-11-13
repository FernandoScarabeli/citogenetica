import { describe, expect, it, vi } from 'vitest';
import { createContagemApi } from '../src/api/contagem-api';

function setup() {
  const request = vi.fn().mockResolvedValue({});
  const download = vi.fn().mockResolvedValue({ blob: new Blob(), nome: 'a.csv' });
  return { api: createContagemApi({ request, download } as never), request, download };
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

describe('exportação', () => {
  it('baixa os arquivos de uma lâmina, com nome padrão e sinal', async () => {
    const { api, download } = setup();
    await api.baixarLaminaJson('l 1', SIGNAL);
    await api.baixarLaminaResumoCsv('l1');
    await api.baixarLaminaRegistrosCsv('l1');
    await api.baixarLaminaRelatorioPdf('l1');
    expect(download).toHaveBeenNthCalledWith(1, '/api/v1/laminas/l%201/exportacao.json', 'lamina.json', { signal: SIGNAL });
    expect(download).toHaveBeenNthCalledWith(2, '/api/v1/laminas/l1/resumo.csv', 'lamina-resumo.csv', {});
    expect(download).toHaveBeenNthCalledWith(3, '/api/v1/laminas/l1/registros.csv', 'lamina-registros.csv', {});
    expect(download).toHaveBeenNthCalledWith(4, '/api/v1/laminas/l1/relatorio.pdf', 'lamina-relatorio.pdf', {});
  });

  it('baixa várias lâminas com os filtros da listagem', async () => {
    const { api, download } = setup();
    await api.baixarLaminasJson();
    await api.baixarLaminasResumoCsv({ laboratorioId: 'lab-1' });
    await api.baixarLaminasRegistrosCsv({ laboratorioId: 'lab-1', donoUsuarioId: 7, signal: SIGNAL });
    await api.baixarLaminasJson({ laboratorioId: null, donoUsuarioId: 0 });
    expect(download).toHaveBeenNthCalledWith(1, '/api/v1/laminas/exportacao.json', 'laminas.json', {});
    expect(download).toHaveBeenNthCalledWith(2, '/api/v1/laminas/resumo.csv?laboratorioId=lab-1', 'laminas-resumo.csv', {});
    expect(download).toHaveBeenNthCalledWith(3, '/api/v1/laminas/registros.csv?laboratorioId=lab-1&donoUsuarioId=7', 'laminas-registros.csv', { signal: SIGNAL });
    expect(download).toHaveBeenNthCalledWith(4, '/api/v1/laminas/exportacao.json?donoUsuarioId=0', 'laminas.json', {});
  });
});
