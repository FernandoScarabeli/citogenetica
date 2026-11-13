import { describe, expect, it, vi } from 'vitest';
import { ApiError, NetworkError } from '../src/api/errors';
import { createHttpClient, nomeDeContentDisposition } from '../src/api/http-client';

function client(response: Response | Error) {
  const fetch = vi.fn(async () => { if (response instanceof Error) throw response; return response; });
  return { http: createHttpClient({ fetch: fetch as never }), fetch };
}
const erro = (status: number, message: string) => new Response(JSON.stringify({ status, error: 'Erro', message, path: '/x' }), {
  status, headers: { 'content-type': 'application/json' },
});

describe('nome do arquivo em Content-Disposition', () => {
  it('prefere filename* em UTF-8, com acentos e emoji', () => {
    const nome = 'lâmina 🌱 ação.csv';
    const header = `attachment; filename="lamina.csv"; filename*=UTF-8''${encodeURIComponent(nome)}`;
    expect(nomeDeContentDisposition(header)).toBe(nome);
    expect(nomeDeContentDisposition(`attachment; filename*=utf-8''l%C3%A2mina.json`)).toBe('lâmina.json');
  });

  it('cai para filename, com ou sem aspas, e ignora caminhos', () => {
    expect(nomeDeContentDisposition('attachment; filename="lamina; 1.csv"')).toBe('lamina; 1.csv');
    expect(nomeDeContentDisposition('attachment; filename=lamina.csv')).toBe('lamina.csv');
    expect(nomeDeContentDisposition('attachment; filename="../../etc/passwd"')).toBe('passwd');
  });

  it('cai para filename quando filename* é inválido e devolve nada sem nome', () => {
    expect(nomeDeContentDisposition(`attachment; filename="a.csv"; filename*=UTF-8''%E0%A4%A`)).toBe('a.csv');
    expect(nomeDeContentDisposition(null)).toBeUndefined();
    expect(nomeDeContentDisposition('attachment')).toBeUndefined();
  });
});

describe('download pelo cliente HTTP', () => {
  it('devolve o Blob com o nome da API e usa os cookies da sessão', async () => {
    const { http, fetch } = client(new Response('a;b', {
      headers: { 'content-disposition': `attachment; filename*=UTF-8''lamina-%F0%9F%8C%B1.csv` },
    }));
    const signal = new AbortController().signal;
    const arquivo = await http.download('/api/v1/laminas/l1/resumo.csv', 'padrao.csv', { signal });
    expect(arquivo.nome).toBe('lamina-🌱.csv');
    expect(await arquivo.blob.text()).toBe('a;b');
    expect(fetch).toHaveBeenCalledWith('/api/v1/laminas/l1/resumo.csv', expect.objectContaining({ method: 'GET', credentials: 'include', signal }));
  });

  it('usa o nome padrão quando a API não indica um', async () => {
    const { http } = client(new Response('x'));
    expect((await http.download('/x', 'padrao.pdf')).nome).toBe('padrao.pdf');
  });

  it.each([401, 403, 404])('transforma %i em ApiError', async status => {
    const { http } = client(erro(status, 'Mensagem da API.'));
    const falha = await http.download('/x', 'p').catch(e => e);
    expect(falha).toBeInstanceOf(ApiError);
    expect(falha.status).toBe(status);
    expect(falha.message).toBe('Mensagem da API.');
  });

  it('avisa quando a sessão expirou (401)', async () => {
    const { http } = client(erro(401, 'Sessão expirada.'));
    const ouvinte = vi.fn();
    http.onUnauthorized(ouvinte);
    await http.download('/x', 'p').catch(() => undefined);
    expect(ouvinte).toHaveBeenCalledOnce();
  });

  it('falha de rede vira NetworkError', async () => {
    const { http } = client(new TypeError('Failed to fetch'));
    await expect(http.download('/x', 'p')).rejects.toBeInstanceOf(NetworkError);
  });
});
