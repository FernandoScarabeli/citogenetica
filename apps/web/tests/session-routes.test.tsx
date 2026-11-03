import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { httpClient } from '../src/api/http-client';
import { App } from '../src/app/App';

const userDto = {
  id: 42,
  version: 0,
  createdDate: '2026-11-03T12:00:00',
  lastModifiedDate: null,
  nome: 'Ana Pesquisadora',
  email: 'ana@ufla.br',
  status: 'ATIVO',
  role: { id: 1, version: 0, createdDate: '2026-11-03T12:00:00', lastModifiedDate: null, nome: 'USER' },
};

function response(status: number, body?: unknown, headers: Record<string, string> = {}) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: body === undefined ? headers : { 'content-type': 'application/json', ...headers },
  });
}

function unauthorized(path: string) {
  return response(401, {
    status: 401,
    error: 'Unauthorized',
    message: 'Falha na autenticação.',
    path,
  });
}

function setPath(path: string, state?: unknown) {
  window.history.replaceState(state ?? null, '', path);
}

function mockApi(options: {
  me?: () => Response | Promise<Response>;
  login?: () => Response | Promise<Response>;
  logout?: () => Response | Promise<Response>;
  other?: () => Response | Promise<Response>;
} = {}) {
  const calls: Array<{ path: string; method: string }> = [];
  let csrfCount = 0;
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = new URL(String(input), window.location.origin).pathname;
    const method = init?.method ?? 'GET';
    calls.push({ path, method });

    if (path === '/api/v1/auth/csrf') {
      csrfCount += 1;
      document.cookie = `XSRF-TOKEN=csrf-${csrfCount}; Path=/`;
      return response(204);
    }
    if (path === '/api/v1/usuarios/me') return options.me ? options.me() : unauthorized(path);
    if (path === '/api/v1/notificacoes/nao-lidas/count') return response(200, { count: 0 });
    if (path === '/api/v1/notificacoes') return response(200, { content: [], number: 0, totalPages: 0, last: true });
    if (path === '/api/v1/laboratorios' && method === 'GET') return response(200, []);
    if (path === '/api/v1/auth/login') return options.login ? options.login() : response(204);
    if (path === '/api/v1/auth/logout') return options.logout ? options.logout() : response(204);
    return options.other ? options.other() : unauthorized(path);
  });

  vi.stubGlobal('fetch', fetchMock);
  return { calls, fetchMock };
}

async function enterCredentials(user: ReturnType<typeof userEvent.setup> = userEvent.setup()) {
  await user.type(screen.getByRole('textbox', { name: 'E-mail' }), 'ana@ufla.br');
  await user.type(screen.getByLabelText('Senha'), 'senha-correta');
  return user;
}

async function submitLogin(user: ReturnType<typeof userEvent.setup> = userEvent.setup()) {
  await enterCredentials(user);
  await user.click(screen.getByRole('button', { name: 'Entrar' }));
  return user;
}

describe('sessão e rotas', () => {
  beforeEach(() => {
    setPath('/');
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('inicia logada e exibe a identidade devolvida pela API', async () => {
    mockApi({ me: async () => response(200, userDto) });
    setPath('/laboratorios');

    render(<App/>);

    expect(await screen.findByRole('heading', { name: 'Meus laboratórios' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Menu do perfil' }).textContent).toContain('Ana Pesquisadora');
    expect(screen.getByRole('button', { name: 'Menu do perfil' }).textContent).toContain('ana@ufla.br');
  });

  it('inicia deslogada, protege a rota e volta ao endereço interno depois do login', async () => {
    let meCalls = 0;
    mockApi({
      me: async () => {
        meCalls += 1;
        return meCalls === 1 ? unauthorized('/api/v1/usuarios/me') : response(200, userDto);
      },
      login: async () => response(204),
    });
    setPath('/laboratorios?filtro=amostra#lista');

    render(<App/>);
    expect(await screen.findByRole('heading', { name: 'Acesse sua pesquisa.' })).toBeTruthy();
    const user = userEvent.setup();
    await submitLogin(user);
    await screen.findByRole('heading', { name: 'Meus laboratórios' });

    expect(window.location.pathname + window.location.search + window.location.hash)
      .toBe('/laboratorios?filtro=amostra#lista');
  });

  it('mostra falha de rede ao consultar a sessão e permite tentar de novo', async () => {
    let attempts = 0;
    mockApi({
      me: async () => {
        attempts += 1;
        if (attempts === 1) throw new TypeError('offline');
        return response(200, userDto);
      },
    });
    setPath('/app');

    render(<App/>);

    expect(await screen.findByRole('heading', { name: 'Não foi possível verificar sua sessão' })).toBeTruthy();
    await userEvent.setup().click(screen.getByRole('button', { name: 'Tentar de novo' }));
    expect(await screen.findByRole('heading', { name: 'Meus laboratórios' })).toBeTruthy();
    expect(attempts).toBe(2);
  });

  it('ignora um caminho externo informado para retorno após login', async () => {
    let meCalls = 0;
    mockApi({
      me: async () => {
        meCalls += 1;
        return meCalls === 1 ? unauthorized('/api/v1/usuarios/me') : response(200, userDto);
      },
      login: async () => response(204),
    });
    setPath('/entrar', {
      usr: { returnTo: 'https://outro-site.example/caminho' },
      key: 'external-return',
      idx: 0,
    });

    render(<App/>);
    await submitLogin();
    await screen.findByRole('heading', { name: 'Meus laboratórios' });

    expect(window.location.pathname).toBe('/app');
    expect(window.location.hostname).toBe('localhost');
  });

  it('mantém 401 de login na própria tela com mensagem genérica', async () => {
    const { calls } = mockApi({
      me: async () => unauthorized('/api/v1/usuarios/me'),
      login: async () => unauthorized('/api/v1/auth/login'),
    });
    setPath('/entrar');

    render(<App/>);
    await submitLogin();

    expect((await screen.findByRole('alert')).textContent).toContain('E-mail ou senha inválidos.');
    expect(screen.getByLabelText('E-mail').getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(screen.getByRole('alert'));
    expect(window.location.pathname).toBe('/entrar');
    expect(calls.filter((call) => call.path === '/api/v1/auth/login')).toHaveLength(1);
  });

  it('aplica o tempo informado em Retry-After para respostas 429', async () => {
    mockApi({
      me: async () => unauthorized('/api/v1/usuarios/me'),
    login: async () => response(429, undefined, { 'Retry-After': '1' }),
    });
    setPath('/entrar');
    render(<App/>);
    await screen.findByRole('heading', { name: 'Acesse sua pesquisa.' });
    fireEvent.change(screen.getByRole('textbox', { name: 'E-mail' }), { target: { value: 'ana@ufla.br' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'senha-correta' } });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect((await screen.findByRole('alert')).textContent).toContain('Tente novamente em 1 segundo.');
    expect((screen.getByRole('button', { name: 'Aguarde 1 s' }) as HTMLButtonElement).disabled).toBe(true);
    await waitFor(
      () => expect(screen.getByRole('status').textContent).toContain('Você já pode tentar novamente.'),
      { timeout: 3000 },
    );
    expect((screen.getByRole('button', { name: 'Entrar' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('encerra a sessão pelo endpoint, renova CSRF e abre a tela de login', async () => {
    const { calls } = mockApi({ me: async () => response(200, userDto) });
    setPath('/app');

    render(<App/>);
    await screen.findByRole('heading', { name: 'Meus laboratórios' });
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Menu do perfil' }));
    await user.click(screen.getByRole('button', { name: 'Sair' }));

    expect(await screen.findByRole('heading', { name: 'Acesse sua pesquisa.' })).toBeTruthy();
    const logoutIndex = calls.findIndex((call) => call.path === '/api/v1/auth/logout');
    expect(logoutIndex).toBeGreaterThanOrEqual(0);
    expect(calls[logoutIndex + 1]).toEqual({ path: '/api/v1/auth/csrf', method: 'GET' });
  });

  it('qualquer 401 após login encerra a sessão e guarda o caminho interno atual', async () => {
    const { calls } = mockApi({
      me: async () => response(200, userDto),
      other: async () => unauthorized('/api/recurso'),
      login: async () => response(204),
    });
    setPath('/laboratorios?aba=registros');
    render(<App/>);
    await screen.findByRole('heading', { name: 'Meus laboratórios' });

    await act(async () => {
      await httpClient.request('/api/recurso').catch(() => undefined);
    });
    expect(await screen.findByRole('heading', { name: 'Acesse sua pesquisa.' })).toBeTruthy();
    await submitLogin();
    await screen.findByRole('heading', { name: 'Meus laboratórios' });

    expect(window.location.pathname + window.location.search).toBe('/laboratorios?aba=registros');
    expect(calls.some((call) => call.path === '/api/recurso')).toBe(true);
  });

  it('renderiza uma página 404 acessível para caminhos sem rota', async () => {
    mockApi();
    setPath('/rota-inexistente');

    render(<App/>);

    expect(screen.getByRole('heading', { name: 'Página não encontrada' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Voltar à página inicial' })).toBeTruthy();
  });
});
