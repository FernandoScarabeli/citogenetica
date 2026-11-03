import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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

function apiError(status: number, path: string, message: string) {
  return response(status, { status, error: 'Bad Request', message, path });
}

function mockApi(options: {
  authenticated?: boolean;
  accept?: () => Response | Promise<Response>;
  refuse?: () => Response | Promise<Response>;
} = {}) {
  let authenticated = options.authenticated ?? false;
  let csrfCount = 0;
  const calls: Array<{ path: string; method: string; body?: unknown; hashAtRequest: string }> = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), window.location.origin);
    const path = url.pathname;
    const method = init?.method ?? 'GET';
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) as unknown : undefined;
    calls.push({ path, method, body, hashAtRequest: window.location.hash });

    if (path === '/api/v1/auth/csrf') {
      csrfCount += 1;
      document.cookie = `XSRF-TOKEN=csrf-${csrfCount}; Path=/`;
      return response(204);
    }
    if (path === '/api/v1/usuarios/me') {
      return authenticated ? response(200, userDto) : apiError(401, path, 'Falha na autenticação.');
    }
    if (path === '/api/v1/auth/login') {
      authenticated = true;
      return response(204);
    }
    if (path === '/api/v1/laboratorios' && method === 'GET') return response(200, []);
    if (path === '/api/v1/convites/aceitar') return options.accept ? options.accept() : response(204);
    if (path === '/api/v1/convites/recusar') return options.refuse ? options.refuse() : response(204);
    return apiError(404, path, 'Não encontrado.');
  });

  vi.stubGlobal('fetch', fetchMock);
  return { calls, fetchMock };
}

function openInvitation(token = 'abc%2B123') {
  window.history.replaceState(null, '', `/convites/aceitar#token=${token}`);
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
  window.history.replaceState(null, '', '/');
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('aceite de convites', () => {
  it('remove o token antes de requisições e mantém-no em memória no retorno do login', async () => {
    const { calls } = mockApi({ authenticated: false });
    openInvitation();
    render(<App/>);

    expect(await screen.findByRole('link', { name: 'Entrar' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Criar conta' })).toBeTruthy();
    expect(screen.getByText(/abra novamente o link do convite depois da confirmação/)).toBeTruthy();
    expect(window.location.hash).toBe('');

    const user = userEvent.setup();
    await user.click(screen.getByRole('link', { name: 'Entrar' }));
    await screen.findByRole('heading', { name: 'Acesse sua pesquisa.' });
    await user.type(screen.getByRole('textbox', { name: 'E-mail' }), 'ana@ufla.br');
    await user.type(screen.getByLabelText('Senha'), 'senha-correta');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('button', { name: 'Aceitar convite' })).toBeTruthy();
    expect(window.location.pathname).toBe('/convites/aceitar');
    expect(window.location.hash).toBe('');
    await user.click(screen.getByRole('button', { name: 'Aceitar convite' }));

    expect(await screen.findByRole('heading', { name: 'Nenhum laboratório ainda' })).toBeTruthy();
    expect((await screen.findByRole('status')).textContent).toContain('Convite aceito. O laboratório foi adicionado à sua lista.');
    const acceptCalls = calls.filter(call => call.path === '/api/v1/convites/aceitar');
    expect(acceptCalls).toHaveLength(1);
    expect(acceptCalls[0].body).toEqual({ token: 'abc+123' });
    expect(calls.every(call => call.hashAtRequest === '')).toBe(true);
  });

  it('envia uma única solicitação de aceite com sessão autenticada', async () => {
    const { calls } = mockApi({ authenticated: true });
    openInvitation('token-unico');
    render(<App/>);
    const accept = await screen.findByRole('button', { name: 'Aceitar convite' });
    await userEvent.setup().click(accept);

    await screen.findByRole('heading', { name: 'Nenhum laboratório ainda' });
    expect(calls.filter(call => call.path === '/api/v1/convites/aceitar')).toHaveLength(1);
    expect(calls.find(call => call.path === '/api/v1/convites/aceitar')?.body).toEqual({ token: 'token-unico' });
    expect(calls.every(call => call.hashAtRequest === '')).toBe(true);
  });

  it('recusa o convite com sessão autenticada e confirma o resultado', async () => {
    const { calls } = mockApi({ authenticated: true });
    openInvitation('token-recusado');
    render(<App/>);
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Recusar' }));

    expect(await screen.findByRole('heading', { name: 'Convite recusado' })).toBeTruthy();
    expect((await screen.findByText('Você recusou o convite.', { selector: '[role="status"]' })).textContent).toContain('Você recusou o convite.');
    expect(calls.filter(call => call.path === '/api/v1/convites/recusar')).toHaveLength(1);
    expect(calls.find(call => call.path === '/api/v1/convites/recusar')?.body).toEqual({ token: 'token-recusado' });
  });

  it('usa uma mensagem genérica para resposta 400', async () => {
    mockApi({
      authenticated: true,
      accept: async () => apiError(400, '/api/v1/convites/aceitar', 'Não revele este detalhe interno.'),
    });
    openInvitation('token-invalido');
    render(<App/>);
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Aceitar convite' }));

    expect(await screen.findByRole('heading', { name: 'Convite indisponível' })).toBeTruthy();
    expect(screen.getByRole('alert').textContent).toContain('Convite inválido ou indisponível. Ele pode ter expirado, sido cancelado ou ser para outro e-mail.');
    expect(screen.queryByText('Não revele este detalhe interno.')).toBeNull();
  });

  it('mostra o tempo de espera informado em Retry-After', async () => {
    const { calls } = mockApi({
      authenticated: true,
      accept: async () => response(429, { status: 429, error: 'Too Many Requests', message: 'Limite atingido.', path: '/api/v1/convites/aceitar' }, { 'Retry-After': '4' }),
    });
    openInvitation('token-limitado');
    render(<App/>);
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Aceitar convite' }));

    expect((await screen.findByRole('alert')).textContent).toContain('Tente novamente em 4 segundos.');
    expect((screen.getByRole('button', { name: 'Aguarde 4 s' }) as HTMLButtonElement).disabled).toBe(true);
    expect(calls.filter(call => call.path === '/api/v1/convites/aceitar')).toHaveLength(1);
  });
});
