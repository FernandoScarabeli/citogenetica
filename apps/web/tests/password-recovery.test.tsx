import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../src/app/App';
import { passwordPolicyError } from '../src/app/registration-validation';

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

type Reply = Response | Promise<Response>;
interface RecoveryOptions {
  me?: () => Reply;
  request?: (body: unknown) => Reply;
  validate?: (body: unknown) => Reply;
  reset?: (body: unknown) => Reply;
}

function response(status: number, body?: unknown, headers: Record<string, string> = {}) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: body === undefined ? headers : { 'content-type': 'application/json', ...headers },
  });
}

function apiError(status: number, message: string, headers: Record<string, string> = {}) {
  return response(status, {
    status,
    error: status === 429 ? 'Too Many Requests' : 'Bad Request',
    message,
    path: '/api/v1/auth/validar-codigo',
  }, headers);
}

function setPath(path: string) {
  window.history.replaceState(null, '', path);
}

function mockApi(options: RecoveryOptions = {}) {
  const calls: Array<{ path: string; method: string; body?: unknown; url: string }> = [];
  let csrfCount = 0;
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), window.location.origin);
    const method = init?.method ?? 'GET';
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) as unknown : undefined;
    calls.push({ path: url.pathname, method, body, url: url.href });

    if (url.pathname === '/api/v1/auth/csrf') {
      csrfCount += 1;
      document.cookie = `XSRF-TOKEN=csrf-${csrfCount}; Path=/`;
      return response(204);
    }
    if (url.pathname === '/api/v1/usuarios/me') {
      return options.me ? options.me() : response(401, {
        status: 401, error: 'Unauthorized', message: 'Falha na autenticação.', path: url.pathname,
      });
    }
    if (url.pathname === '/api/v1/auth/recuperar-senha') return options.request ? options.request(body) : response(202);
    if (url.pathname === '/api/v1/auth/validar-codigo') return options.validate ? options.validate(body) : response(204);
    if (url.pathname === '/api/v1/auth/redefinir-senha') return options.reset ? options.reset(body) : response(204);
    return response(404, { status: 404, error: 'Not Found', message: 'Não encontrado.', path: url.pathname });
  });

  vi.stubGlobal('fetch', fetchMock);
  return { calls, fetchMock };
}

async function openRecovery(options: RecoveryOptions = {}) {
  const mocked = mockApi(options);
  setPath('/recuperar-senha');
  const view = render(<App/>);
  await screen.findByRole('heading', { name: 'Recuperar senha' });
  return { ...mocked, user: userEvent.setup(), ...view };
}

async function sendCode(user: ReturnType<typeof userEvent.setup>, email = 'ana@ufla.br') {
  await user.type(screen.getByRole('textbox', { name: 'E-mail' }), email);
  await user.click(screen.getByRole('button', { name: 'Enviar código' }));
  await screen.findByRole('heading', { name: 'Confira seu e-mail' });
}

async function enterCode(user: ReturnType<typeof userEvent.setup>, code = '123456') {
  await user.type(screen.getByRole('textbox', { name: 'Código de 6 dígitos' }), code);
}

async function reachPasswordStep(user: ReturnType<typeof userEvent.setup>) {
  await sendCode(user);
  await enterCode(user);
  await user.click(screen.getByRole('button', { name: 'Validar código' }));
  await screen.findByRole('heading', { name: 'Defina uma nova senha' });
}

describe('recuperação de senha', () => {
  beforeEach(() => setPath('/'));

  afterEach(() => vi.clearAllMocks());

  it('mostra no login o link para recuperar senha', async () => {
    mockApi();
    setPath('/entrar');
    render(<App/>);

    const link = await screen.findByRole('link', { name: 'Esqueci a senha' });
    await userEvent.setup().click(link);
    expect(await screen.findByRole('heading', { name: 'Recuperar senha' })).toBeTruthy();
    expect(window.location.pathname).toBe('/recuperar-senha');
  });

  it('primeiro passo só tem Voltar; nos outros, "Usar outro e-mail" volta ao começo', async () => {
    const { user } = await openRecovery();
    expect(screen.queryByRole('button', { name: 'Limpar e começar de novo' })).toBeNull();

    await sendCode(user);
    await user.click(screen.getByRole('button', { name: 'Usar outro e-mail' }));

    expect(await screen.findByRole('heading', { name: 'Recuperar senha' })).toBeTruthy();
    expect((screen.getByRole('textbox', { name: 'E-mail' }) as HTMLInputElement).value).toBe('');
  });

  it('dá sempre uma resposta neutra depois de solicitar o código', async () => {
    const { calls, user } = await openRecovery();
    await sendCode(user, 'nao-cadastrado@ufla.br');

    expect(screen.getByRole('status').textContent).toBe('Se houver uma conta associada a esse endereço, enviaremos um código de 6 dígitos.');
    expect(screen.queryByText('nao-cadastrado@ufla.br')).toBeNull();
    expect(calls.find((call) => call.path === '/api/v1/auth/recuperar-senha')?.body)
      .toEqual({ email: 'nao-cadastrado@ufla.br' });
  });

  it('aceita colar o código, remove tudo que não for dígito e valida seis dígitos', async () => {
    const { calls, user } = await openRecovery();
    await sendCode(user);
    const code = screen.getByRole('textbox', { name: 'Código de 6 dígitos' }) as HTMLInputElement;
    expect(code.getAttribute('inputmode')).toBe('numeric');
    expect(code.getAttribute('autocomplete')).toBe('one-time-code');
    await user.click(code);
    await user.paste('12 a34 56');
    expect(code.value).toBe('123456');
    await user.click(screen.getByRole('button', { name: 'Validar código' }));

    expect(await screen.findByRole('heading', { name: 'Defina uma nova senha' })).toBeTruthy();
    expect(calls.find((call) => call.path === '/api/v1/auth/validar-codigo')?.body)
      .toEqual({ email: 'ana@ufla.br', codigo: '123456' });
  });

  it('valida o formato localmente antes de chamar a API', async () => {
    const { calls, user } = await openRecovery();
    await sendCode(user);
    await user.type(screen.getByRole('textbox', { name: 'Código de 6 dígitos' }), '123');
    await user.click(screen.getByRole('button', { name: 'Validar código' }));
    expect(screen.getByText('Informe os seis dígitos do código.')).toBeTruthy();
    expect(calls.some((call) => call.path === '/api/v1/auth/validar-codigo')).toBe(false);
  });

  it.each(['incorreto', 'expirado', 'tentativas esgotadas'])('mantém genérica a resposta para código %s', async () => {
    const { user } = await openRecovery({ validate: () => apiError(400, 'Código inválido ou expirado.') });
    await sendCode(user);
    await enterCode(user);
    await user.click(screen.getByRole('button', { name: 'Validar código' }));

    const code = screen.getByRole('textbox', { name: 'Código de 6 dígitos' });
    expect(code.getAttribute('aria-invalid')).toBe('true');
    expect(screen.getByText('Código inválido ou expirado. Confira os seis dígitos ou solicite um novo código.')).toBeTruthy();
  });

  it('aplica Retry-After ao pedido e libera o envio quando a contagem termina', async () => {
    let requests = 0;
    const { calls, user } = await openRecovery({
      request: () => {
        requests += 1;
        return requests === 1 ? apiError(429, 'Muitas tentativas. Tente novamente mais tarde.', { 'Retry-After': '1' }) : response(202);
      },
    });
    await user.type(screen.getByRole('textbox', { name: 'E-mail' }), 'ana@ufla.br');
    await user.click(screen.getByRole('button', { name: 'Enviar código' }));
    expect((await screen.findByRole('alert')).textContent).toContain('Tente novamente em 1 segundo.');
    expect((screen.getByRole('button', { name: 'Enviar código' }) as HTMLButtonElement).disabled).toBe(true);
    await waitFor(() => expect(screen.getByRole('status').textContent).toContain('Você já pode tentar novamente.'), { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: 'Enviar código' }));
    expect(await screen.findByRole('heading', { name: 'Confira seu e-mail' })).toBeTruthy();
    expect(calls.filter((call) => call.path === '/api/v1/auth/recuperar-senha')).toHaveLength(2);
  });

  it('aplica Retry-After ao validar o código e também usa o limite no reenvio', async () => {
    let validations = 0;
    let requests = 0;
    const { calls, user } = await openRecovery({
      request: () => {
        requests += 1;
        return requests === 1 ? response(202) : apiError(429, 'Muitas tentativas.', { 'Retry-After': '1' });
      },
      validate: () => {
        validations += 1;
        return validations === 1 ? apiError(429, 'Muitas tentativas.', { 'Retry-After': '1' }) : response(204);
      },
    });
    await sendCode(user);
    await enterCode(user);
    await user.click(screen.getByRole('button', { name: 'Validar código' }));
    expect((await screen.findByRole('alert')).textContent).toContain('Tente novamente em 1 segundo.');
    expect((screen.getByRole('button', { name: 'Validar código' }) as HTMLButtonElement).disabled).toBe(true);
    await waitFor(() => expect(screen.getByRole('status').textContent).toContain('Você já pode tentar novamente.'), { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: 'Validar código' }));
    expect(await screen.findByRole('heading', { name: 'Defina uma nova senha' })).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Voltar para o código' }));
    await screen.findByRole('heading', { name: 'Confira seu e-mail' });
    await user.click(screen.getByRole('button', { name: 'Reenviar código' }));
    expect((await screen.findByRole('alert')).textContent).toContain('Tente novamente em 1 segundo.');
    expect((screen.getByRole('button', { name: 'Reenviar código' }) as HTMLButtonElement).disabled).toBe(true);
    expect(calls.filter((call) => call.path === '/api/v1/auth/recuperar-senha')).toHaveLength(2);
  });

  it('reaproveita as regras de senha do cadastro e valida a confirmação', async () => {
    const { calls, user } = await openRecovery();
    await reachPasswordStep(user);
    await user.type(screen.getByLabelText('Nova senha'), 'curta');
    await user.type(screen.getByLabelText('Confirme sua nova senha'), 'curta');
    await user.click(screen.getByRole('button', { name: 'Redefinir senha' }));
    expect(screen.getByText(passwordPolicyError('curta')!)).toBeTruthy();

    const password = screen.getByLabelText('Nova senha');
    await user.clear(password);
    await user.type(password, 'A'.repeat(65));
    const confirmation = screen.getByLabelText('Confirme sua nova senha');
    await user.clear(confirmation);
    await user.type(confirmation, 'A'.repeat(65));
    await user.click(screen.getByRole('button', { name: 'Redefinir senha' }));
    expect(screen.getByText(passwordPolicyError('A'.repeat(65))!)).toBeTruthy();

    await user.clear(password);
    await user.type(password, 'AmostraSegura-783!');
    await user.clear(confirmation);
    await user.type(confirmation, 'senha-diferente');
    await user.click(screen.getByRole('button', { name: 'Redefinir senha' }));
    expect(screen.getByText('As senhas não coincidem.')).toBeTruthy();
    expect(calls.some((call) => call.path === '/api/v1/auth/redefinir-senha')).toBe(false);
  });

  it('envia apenas os campos do DTO, conclui a redefinição e informa o encerramento das sessões', async () => {
    const { calls, user } = await openRecovery();
    await reachPasswordStep(user);
    await user.type(screen.getByLabelText('Nova senha'), 'AmostraSegura-783!');
    await user.type(screen.getByLabelText('Confirme sua nova senha'), 'AmostraSegura-783!');
    await user.click(screen.getByRole('button', { name: 'Redefinir senha' }));

    expect(await screen.findByRole('heading', { name: 'Senha redefinida' })).toBeTruthy();
    expect(screen.getByText(/as sessões abertas desta conta foram encerradas/i)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Ir para entrar' })).toBeTruthy();
    expect(calls.find((call) => call.path === '/api/v1/auth/redefinir-senha')?.body).toEqual({
      email: 'ana@ufla.br', codigo: '123456', novaSenha: 'AmostraSegura-783!',
    });
  });

  it('associa à nova senha a rejeição de senha comum enviada pelo servidor', async () => {
    const message = 'Escolha uma senha que não seja comum ou conhecida em vazamentos.';
    const { user } = await openRecovery({ reset: () => apiError(400, message) });
    await reachPasswordStep(user);
    await user.type(screen.getByLabelText('Nova senha'), 'AmostraSegura-783!');
    await user.type(screen.getByLabelText('Confirme sua nova senha'), 'AmostraSegura-783!');
    await user.click(screen.getByRole('button', { name: 'Redefinir senha' }));

    expect(await screen.findByText(message)).toBeTruthy();
    expect(screen.getByLabelText('Nova senha').getAttribute('aria-invalid')).toBe('true');
  });

  it('não inclui e-mail, código ou senha em URL ou armazenamento persistente e reinicia ao montar a rota', async () => {
    const { user, unmount } = await openRecovery();
    await reachPasswordStep(user);
    await user.type(screen.getByLabelText('Nova senha'), 'AmostraSegura-783!');
    await user.type(screen.getByLabelText('Confirme sua nova senha'), 'AmostraSegura-783!');

    expect(window.location.pathname + window.location.search + window.location.hash).toBe('/recuperar-senha');
    expect(window.location.href).not.toContain('ana@ufla.br');
    expect(window.location.href).not.toContain('123456');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);

    unmount();
    render(<App/>);
    expect(await screen.findByRole('heading', { name: 'Recuperar senha' })).toBeTruthy();
    expect((screen.getByRole('textbox', { name: 'E-mail' }) as HTMLInputElement).value).toBe('');
    expect(window.location.pathname + window.location.search + window.location.hash).toBe('/recuperar-senha');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('permite abrir e usar a recuperação enquanto a sessão está autenticada', async () => {
    const { user } = await openRecovery({ me: () => response(200, userDto) });
    await sendCode(user);
    expect(screen.getByRole('heading', { name: 'Confira seu e-mail' })).toBeTruthy();
    expect(window.location.pathname).toBe('/recuperar-senha');
  });

  it('mostra o erro de desafio da redefinição no passo do código', async () => {
    const { user } = await openRecovery({ reset: () => apiError(400, 'Código inválido ou expirado.') });
    await reachPasswordStep(user);
    await user.type(screen.getByLabelText('Nova senha'), 'AmostraSegura-783!');
    await user.type(screen.getByLabelText('Confirme sua nova senha'), 'AmostraSegura-783!');
    await user.click(screen.getByRole('button', { name: 'Redefinir senha' }));

    expect(await screen.findByRole('heading', { name: 'Confira seu e-mail' })).toBeTruthy();
    expect(screen.getByText('Código inválido ou expirado. Confira os seis dígitos ou solicite um novo código.')).toBeTruthy();
  });
});
