import { StrictMode } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../src/app/App';
import { passwordPolicyError, validateRegistration, validateResendEmail } from '../src/app/registration-validation';

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

interface MockResponseOptions {
  me?: () => Response | Promise<Response>;
  register?: (body: unknown) => Response | Promise<Response>;
  resend?: (body: unknown) => Response | Promise<Response>;
  confirm?: (body: unknown) => Response | Promise<Response>;
}

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

function apiError(status: number, message: string, details?: string[], headers: Record<string, string> = {}) {
  return response(status, {
    status,
    error: status === 400 ? 'Bad Request' : 'Too Many Requests',
    message,
    path: '/api/v1/auth/register',
    ...(details ? { details } : {}),
  }, headers);
}

function setPath(path: string) {
  window.history.replaceState(null, '', path);
}

function mockApi(options: MockResponseOptions = {}) {
  const calls: Array<{ path: string; method: string; body?: unknown; hashAtRequest: string }> = [];
  let csrfCount = 0;
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = new URL(String(input), window.location.origin).pathname;
    const method = init?.method ?? 'GET';
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) as unknown : undefined;
    calls.push({ path, method, body, hashAtRequest: window.location.hash });

    if (path === '/api/v1/auth/csrf') {
      csrfCount += 1;
      document.cookie = `XSRF-TOKEN=csrf-${csrfCount}; Path=/`;
      return response(204);
    }
    if (path === '/api/v1/usuarios/me') return options.me ? options.me() : unauthorized(path);
    if (path === '/api/v1/auth/register') return options.register ? options.register(body) : response(202);
    if (path === '/api/v1/auth/reenviar-confirmacao') return options.resend ? options.resend(body) : response(202);
    if (path === '/api/v1/auth/confirmar-email') return options.confirm ? options.confirm(body) : response(204);
    return unauthorized(path);
  });

  vi.stubGlobal('fetch', fetchMock);
  return { calls, fetchMock };
}

async function fillRegistration(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Nome'), 'Ana Pesquisadora');
  await user.type(screen.getByLabelText('E-mail'), 'ana@ufla.br');
  await user.type(screen.getByLabelText('Senha'), 'AmostraSegura-783!');
  await user.type(screen.getByLabelText('Confirme sua senha'), 'AmostraSegura-783!');
}

describe('cadastro e confirmação de e-mail', () => {
  beforeEach(() => setPath('/'));

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('valida nome, e-mail, política de senha, confirmação e limites UTF-8', () => {
    const errors = validateRegistration({ nome: ' ', email: 'ana@', senha: 'curta', confirmacaoSenha: 'diferente' });
    expect(errors).toEqual({
      nome: 'Informe seu nome.',
      email: 'Informe um e-mail válido.',
      senha: 'A senha deve ter entre 8 e 64 caracteres.',
      confirmacaoSenha: 'As senhas não coincidem.',
    });

    const unicodeErrors = validateRegistration({
      nome: 'Ana',
      email: 'ana@ufla.br',
      senha: '😀'.repeat(19),
      confirmacaoSenha: '😀'.repeat(19),
    });
    expect(unicodeErrors.senha).toBe('A senha excede o limite de 72 bytes em UTF-8 do BCrypt.');
    expect(passwordPolicyError('A'.repeat(64))).toBeUndefined();
    expect(passwordPolicyError('A'.repeat(65))).toBe('A senha deve ter entre 8 e 64 caracteres.');
    expect(validateRegistration({
      nome: 'A'.repeat(101),
      email: 'ana@ufla.br',
      senha: 'AmostraSegura-783!',
      confirmacaoSenha: 'AmostraSegura-783!',
    }).nome).toBe('O nome deve ter no máximo 100 caracteres.');
    expect(validateResendEmail('x'.repeat(256) + '@ufla.br')).toBe('O e-mail deve ter no máximo 255 caracteres.');
  });

  it('marca os campos inválidos e move o foco para o primeiro erro', async () => {
    mockApi();
    setPath('/criar-conta');
    render(<App/>);
    await screen.findByRole('heading', { name: 'Crie sua conta.' });

    await userEvent.setup().click(screen.getByRole('button', { name: 'Criar conta' }));

    const name = screen.getByLabelText('Nome');
    expect(name.getAttribute('aria-invalid')).toBe('true');
    expect(screen.getByLabelText('E-mail').getAttribute('aria-invalid')).toBe('true');
    expect(screen.getByLabelText('Senha').getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(name);
  });

  it('mostra o erro 400 da política de senha no campo de senha', async () => {
    mockApi({
      register: async () => apiError(400, 'Escolha uma senha que não seja comum ou conhecida em vazamentos.'),
    });
    setPath('/criar-conta');
    render(<App/>);
    await screen.findByRole('heading', { name: 'Crie sua conta.' });
    const user = userEvent.setup();
    await fillRegistration(user);
    await user.click(screen.getByRole('button', { name: 'Criar conta' }));

    const password = screen.getByLabelText('Senha');
    expect(await screen.findByText('Escolha uma senha que não seja comum ou conhecida em vazamentos.')).toBeTruthy();
    expect(password.getAttribute('aria-invalid')).toBe('true');
    expect(password.getAttribute('aria-describedby')).toContain('signup-password-error');
    expect(document.activeElement).toBe(password);
  });

  it('usa os erros de campo enviados em ApiError', async () => {
    mockApi({
      register: async () => apiError(400, 'Erro de validação', ['email: Email inválido']),
    });
    setPath('/criar-conta');
    render(<App/>);
    await screen.findByRole('heading', { name: 'Crie sua conta.' });
    const user = userEvent.setup();
    await fillRegistration(user);
    await user.click(screen.getByRole('button', { name: 'Criar conta' }));

    const email = screen.getByLabelText('E-mail');
    expect(await screen.findByText('Email inválido')).toBeTruthy();
    expect(email.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(email);
  });

  it('trata 202 com uma confirmação neutra e envia apenas campos do contrato', async () => {
    const { calls } = mockApi();
    setPath('/criar-conta');
    render(<App/>);
    await screen.findByRole('heading', { name: 'Crie sua conta.' });
    const user = userEvent.setup();
    await fillRegistration(user);
    await user.click(screen.getByRole('button', { name: 'Criar conta' }));

    expect(await screen.findByRole('heading', { name: 'Confira seu e-mail' })).toBeTruthy();
    expect(screen.getByText(/Se houver uma confirmação de cadastro pendente/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Reenviar e-mail' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Ir para entrar' })).toBeTruthy();
    expect(document.activeElement).toBe(screen.getByRole('heading', { name: 'Confira seu e-mail' }));
    expect(calls.find((call) => call.path === '/api/v1/auth/register')?.body).toEqual({
      nome: 'Ana Pesquisadora',
      email: 'ana@ufla.br',
      senha: 'AmostraSegura-783!',
    });
  });

  it('respeita Retry-After ao reenviar e libera a ação ao fim da contagem', async () => {
    let resendCalls = 0;
    const { calls } = mockApi({
      resend: async () => {
        resendCalls += 1;
        return resendCalls === 1
          ? apiError(429, 'Muitas solicitações.', undefined, { 'Retry-After': '1' })
          : response(202);
      },
    });
    setPath('/criar-conta');
    render(<App/>);
    await screen.findByRole('heading', { name: 'Crie sua conta.' });
    const user = userEvent.setup();
    await fillRegistration(user);
    await user.click(screen.getByRole('button', { name: 'Criar conta' }));
    await screen.findByRole('heading', { name: 'Confira seu e-mail' });

    await user.click(screen.getByRole('button', { name: 'Reenviar e-mail' }));
    expect((await screen.findByRole('alert')).textContent).toContain('Tente novamente em 1 segundo.');
    expect((screen.getByRole('button', { name: 'Reenviar e-mail' }) as HTMLButtonElement).disabled).toBe(true);
    await waitFor(() => expect(screen.getByRole('status').textContent).toContain('Você já pode tentar novamente.'), { timeout: 3000 });
    expect((screen.getByRole('button', { name: 'Reenviar e-mail' }) as HTMLButtonElement).disabled).toBe(false);

    await user.click(screen.getByRole('button', { name: 'Reenviar e-mail' }));
    expect((await screen.findByRole('status')).textContent).toContain('Se houver uma confirmação pendente');
    expect(calls.filter((call) => call.path === '/api/v1/auth/reenviar-confirmacao')).toHaveLength(2);
    expect(calls.find((call) => call.path === '/api/v1/auth/reenviar-confirmacao')?.body).toEqual({ email: 'ana@ufla.br' });
  });

  it('remove o token da URL antes da chamada e confirma uma única vez sob StrictMode', async () => {
    const { calls } = mockApi();
    setPath('/confirmar-email?origem=mensagem#token=abc%2B123');
    render(<StrictMode><App/></StrictMode>);

    expect(await screen.findByRole('heading', { name: 'E-mail confirmado' })).toBeTruthy();
    const confirmationCalls = calls.filter((call) => call.path === '/api/v1/auth/confirmar-email');
    expect(confirmationCalls).toHaveLength(1);
    expect(confirmationCalls[0].hashAtRequest).toBe('');
    expect(confirmationCalls[0].body).toEqual({ token: 'abc+123' });
    expect(calls.every((call) => call.hashAtRequest === '')).toBe(true);
    expect(window.location.pathname + window.location.search + window.location.hash)
      .toBe('/confirmar-email?origem=mensagem');
    expect(calls.some((call) => call.path === '/api/v1/auth/login')).toBe(false);
  });

  it('mostra o formulário de reenvio se o servidor rejeitar o link com 400', async () => {
    const { calls } = mockApi({
      confirm: async () => response(400, {
        status: 400,
        error: 'Bad Request',
        message: 'Link inválido ou expirado.',
        path: '/api/v1/auth/confirmar-email',
      }),
    });
    setPath('/confirmar-email#token=expirado');
    render(<App/>);

    expect(await screen.findByRole('heading', { name: 'Link inválido ou expirado' })).toBeTruthy();
    expect(screen.getByLabelText('E-mail')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Reenviar e-mail' })).toBeTruthy();
    expect(calls.filter((call) => call.path === '/api/v1/auth/confirmar-email')).toHaveLength(1);
    expect(window.location.hash).toBe('');
  });

  it('mostra a mesma tela inválida sem token e não chama a confirmação', async () => {
    const { calls } = mockApi();
    setPath('/confirmar-email');
    render(<App/>);

    expect(await screen.findByRole('heading', { name: 'Link inválido ou expirado' })).toBeTruthy();
    expect(screen.getByLabelText('E-mail')).toBeTruthy();
    expect(calls.some((call) => call.path === '/api/v1/auth/confirmar-email')).toBe(false);
  });

  it('permite mostrar e ocultar a senha com um controle acessível', async () => {
    mockApi();
    setPath('/criar-conta');
    render(<App/>);
    await screen.findByRole('heading', { name: 'Crie sua conta.' });
    const password = screen.getByLabelText('Senha') as HTMLInputElement;
    expect(password.type).toBe('password');

    await userEvent.setup().click(screen.getByRole('button', { name: 'Mostrar senha' }));
    expect(password.type).toBe('text');
    expect(screen.getByRole('button', { name: 'Ocultar senha' }).getAttribute('aria-pressed')).toBe('true');
  });

  it('adiciona a ação Criar conta ao login e à landing', async () => {
    mockApi();
    setPath('/entrar');
    const { unmount } = render(<App/>);
    await screen.findByRole('heading', { name: 'Acesse sua pesquisa.' });
    expect(screen.getByRole('link', { name: 'Criar conta' }).getAttribute('href')).toBe('/criar-conta');
    unmount();

    setPath('/');
    render(<App/>);
    expect(screen.getByRole('link', { name: 'Criar conta' }).getAttribute('href')).toBe('/criar-conta');
  });

  it('redireciona para /app quando uma pessoa autenticada abre /criar-conta', async () => {
    mockApi({ me: async () => response(200, userDto) });
    setPath('/criar-conta');
    render(<App/>);

    expect(await screen.findByRole('heading', { name: 'Meus laboratórios' })).toBeTruthy();
    expect(window.location.pathname).toBe('/app');
    expect(screen.queryByRole('heading', { name: 'Crie sua conta.' })).toBeNull();
  });
});
