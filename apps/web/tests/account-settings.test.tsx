import { StrictMode } from 'react';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../src/app/App';

const userDto = {
  id: 42,
  version: 3,
  createdDate: '2026-11-03T12:00:00',
  lastModifiedDate: '2026-11-03T12:00:00',
  nome: 'Ana Pesquisadora',
  email: 'ana@ufla.br',
  status: 'ATIVO',
  role: { id: 1, version: 0, createdDate: '2026-11-03T12:00:00', lastModifiedDate: null, nome: 'USER' },
};

type Reply = Response | Promise<Response>;
interface AccountOptions {
  me?: () => Reply;
  updateName?: (body: unknown) => Reply;
  updatePassword?: (body: unknown) => Reply;
  requestEmailChange?: (body: unknown) => Reply;
  confirmEmailChange?: (body: unknown) => Reply;
}

function response(status: number, body?: unknown, headers: Record<string, string> = {}) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: body === undefined ? headers : { 'content-type': 'application/json', ...headers },
  });
}

function apiError(status: number, message: string, details?: string[], headers: Record<string, string> = {}) {
  return response(status, {
    status,
    error: status === 429 ? 'Too Many Requests' : status === 409 ? 'Conflict' : 'Bad Request',
    message,
    path: '/api/v1/usuarios/me',
    ...(details ? { details } : {}),
  }, headers);
}

function setPath(path: string) {
  window.history.replaceState(null, '', path);
}

function mockApi(options: AccountOptions = {}) {
  const calls: Array<{ path: string; method: string; body?: unknown; hashAtRequest: string }> = [];
  let csrfCount = 0;
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = new URL(String(input), window.location.origin).pathname;
    const method = init?.method ?? 'GET';
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) as unknown : undefined;
    calls.push({ path, method, body, hashAtRequest: window.location.hash });

    if (path === '/api/v1/auth/csrf') {
      csrfCount += 1;
      document.cookie = 'XSRF-TOKEN=account-' + csrfCount + '; Path=/';
      return response(204);
    }
    if (path === '/api/v1/usuarios/me' && method === 'GET') return options.me ? options.me() : response(200, userDto);
    if (path === '/api/v1/usuarios/me' && method === 'PUT') return options.updateName ? options.updateName(body) : response(200, userDto);
    if (path === '/api/v1/usuarios/me/senha') return options.updatePassword ? options.updatePassword(body) : response(204);
    if (path === '/api/v1/usuarios/me/email') return options.requestEmailChange ? options.requestEmailChange(body) : response(202);
    if (path === '/api/v1/auth/confirmar-troca-email') return options.confirmEmailChange ? options.confirmEmailChange(body) : response(204);
    return response(404, {
      status: 404,
      error: 'Not Found',
      message: 'Não encontrado.',
      path,
    });
  });

  vi.stubGlobal('fetch', fetchMock);
  return { calls, fetchMock };
}

async function openProfile(options: AccountOptions = {}) {
  const mocked = mockApi(options);
  setPath('/laboratorios');
  render(<App/>);
  await screen.findByRole('heading', { name: 'Meus laboratórios' });
  const user = userEvent.setup();
  await user.click(screen.getByRole('button', { name: 'Menu do perfil' }));
  await user.click(screen.getByRole('button', { name: 'Configurações do perfil' }));
  await screen.findByRole('heading', { name: 'Dados pessoais' });
  return { ...mocked, user };
}

async function openSecurity(options: AccountOptions = {}) {
  const opened = await openProfile(options);
  await opened.user.click(screen.getByRole('button', { name: 'Segurança' }));
  await screen.findByRole('heading', { name: 'Alterar senha' });
  return opened;
}

describe('Minha conta', () => {
  beforeEach(() => setPath('/'));

  afterEach(() => vi.clearAllMocks());

  it('salva o nome da conta pela API e atualiza o menu de perfil', async () => {
    const changedUser = { ...userDto, nome: 'Beatriz Pesquisadora', version: 4 };
    const { calls, user } = await openProfile({
      updateName: (body) => {
        expect(body).toEqual({ nome: 'Beatriz Pesquisadora' });
        return response(200, changedUser);
      },
    });

    await user.clear(screen.getByRole('textbox', { name: 'Nome' }));
    await user.type(screen.getByRole('textbox', { name: 'Nome' }), 'Beatriz Pesquisadora');
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));

    expect((await screen.findByRole('status')).textContent).toContain('Nome atualizado.');
    expect(screen.getByRole('button', { name: 'Menu do perfil' }).textContent).toContain('Beatriz Pesquisadora');
    expect(calls.find((call) => call.path === '/api/v1/usuarios/me' && call.method === 'PUT')?.body)
      .toEqual({ nome: 'Beatriz Pesquisadora' });
  });

  it('associa o erro 400 de nome ao campo correspondente', async () => {
    const { user } = await openProfile({
      updateName: () => apiError(400, 'Erro de validação', ['nome: O nome é obrigatório.']),
    });
    await user.clear(screen.getByRole('textbox', { name: 'Nome' }));
    await user.type(screen.getByRole('textbox', { name: 'Nome' }), 'Nome inválido');
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));

    expect(await screen.findByText('O nome é obrigatório.')).toBeTruthy();
    expect(screen.getByRole('textbox', { name: 'Nome' }).getAttribute('aria-invalid')).toBe('true');
  });

  it('troca a senha e informa que as outras sessões foram encerradas', async () => {
    const { calls, user } = await openSecurity();
    await user.type(screen.getAllByLabelText('Senha atual')[0], 'SenhaAtual123!');
    await user.type(screen.getByLabelText('Nova senha'), 'NovaSegura-489!');
    await user.type(screen.getByLabelText('Confirme sua nova senha'), 'NovaSegura-489!');
    await user.click(screen.getByRole('button', { name: 'Alterar senha' }));

    expect((await screen.findByRole('status')).textContent).toContain('Senha alterada. As outras sessões desta conta foram encerradas.');
    expect(calls.find((call) => call.path === '/api/v1/usuarios/me/senha')?.body).toEqual({
      senhaAtual: 'SenhaAtual123!',
      novaSenha: 'NovaSegura-489!',
    });
  });

  it('associa o 409 de senha atual incorreta ao campo atual', async () => {
    const { user } = await openSecurity({
      updatePassword: () => apiError(409, 'A senha atual está incorreta.'),
    });
    await user.type(screen.getAllByLabelText('Senha atual')[0], 'incorreta');
    await user.type(screen.getByLabelText('Nova senha'), 'NovaSegura-489!');
    await user.type(screen.getByLabelText('Confirme sua nova senha'), 'NovaSegura-489!');
    await user.click(screen.getByRole('button', { name: 'Alterar senha' }));

    expect(await screen.findByText('A senha atual está incorreta.')).toBeTruthy();
    expect(screen.getAllByLabelText('Senha atual')[0].getAttribute('aria-invalid')).toBe('true');
  });

  it('reaproveita a política de senha e valida a confirmação antes da chamada', async () => {
    const { calls, user } = await openSecurity();
    await user.type(screen.getAllByLabelText('Senha atual')[0], 'SenhaAtual123!');
    await user.type(screen.getByLabelText('Nova senha'), 'curta');
    await user.type(screen.getByLabelText('Confirme sua nova senha'), 'curta');
    await user.click(screen.getByRole('button', { name: 'Alterar senha' }));
    expect(screen.getByText('A senha deve ter entre 8 e 64 caracteres.')).toBeTruthy();

    await user.clear(screen.getByLabelText('Nova senha'));
    await user.type(screen.getByLabelText('Nova senha'), 'NovaSegura-489!');
    await user.clear(screen.getByLabelText('Confirme sua nova senha'));
    await user.type(screen.getByLabelText('Confirme sua nova senha'), 'OutraSegura-489!');
    await user.click(screen.getByRole('button', { name: 'Alterar senha' }));
    expect(screen.getByText('As senhas não coincidem.')).toBeTruthy();
    expect(calls.some((call) => call.path === '/api/v1/usuarios/me/senha')).toBe(false);
  });

  it('associa rejeições 400 da nova senha ao campo novo', async () => {
    const message = 'Escolha uma senha que não seja comum ou conhecida em vazamentos.';
    const { user } = await openSecurity({
      updatePassword: () => apiError(400, message, ['novaSenha: ' + message]),
    });
    await user.type(screen.getAllByLabelText('Senha atual')[0], 'SenhaAtual123!');
    await user.type(screen.getByLabelText('Nova senha'), 'SenhaPopular-123!');
    await user.type(screen.getByLabelText('Confirme sua nova senha'), 'SenhaPopular-123!');
    await user.click(screen.getByRole('button', { name: 'Alterar senha' }));

    expect(await screen.findByText(message)).toBeTruthy();
    expect(screen.getByLabelText('Nova senha').getAttribute('aria-invalid')).toBe('true');
  });

  it('solicita a troca de e-mail com o payload do contrato e mantém a mensagem neutra', async () => {
    const { calls, user } = await openSecurity();
    await user.type(screen.getByLabelText('Novo e-mail'), 'beatriz@ufla.br');
    await user.type(screen.getAllByLabelText('Senha atual')[1], 'SenhaAtual123!');
    await user.click(screen.getByRole('button', { name: 'Enviar link de confirmação' }));

    expect((await screen.findByRole('status')).textContent).toContain(
      'Enviamos um link para o novo endereço. O e-mail atual continua valendo até você confirmar.',
    );
    expect(calls.find((call) => call.path === '/api/v1/usuarios/me/email')?.body).toEqual({
      novoEmail: 'beatriz@ufla.br',
      senhaAtual: 'SenhaAtual123!',
    });
  });

  it('associa o 409 da troca de e-mail à senha atual', async () => {
    const { user } = await openSecurity({
      requestEmailChange: () => apiError(409, 'A senha atual está incorreta.'),
    });
    await user.type(screen.getByLabelText('Novo e-mail'), 'beatriz@ufla.br');
    const currentPassword = screen.getAllByLabelText('Senha atual')[1];
    await user.type(currentPassword, 'incorreta');
    await user.click(screen.getByRole('button', { name: 'Enviar link de confirmação' }));

    expect(await screen.findByText('A senha atual está incorreta.')).toBeTruthy();
    expect(currentPassword.getAttribute('aria-invalid')).toBe('true');
  });

  it('associa erros 400 do novo e-mail ao campo correspondente', async () => {
    const { user } = await openSecurity({
      requestEmailChange: () => apiError(400, 'Erro de validação', ['novoEmail: Informe um endereço institucional.']),
    });
    await user.type(screen.getByLabelText('Novo e-mail'), 'beatriz@ufla.br');
    await user.type(screen.getAllByLabelText('Senha atual')[1], 'SenhaAtual123!');
    await user.click(screen.getByRole('button', { name: 'Enviar link de confirmação' }));

    const newEmail = await screen.findByLabelText('Novo e-mail');
    expect(screen.getByText('Informe um endereço institucional.')).toBeTruthy();
    expect(newEmail.getAttribute('aria-invalid')).toBe('true');
  });

  it('mostra Retry-After na troca de e-mail e bloqueia novo envio durante a contagem', async () => {
    const { calls, user } = await openSecurity({
      requestEmailChange: () => apiError(429, 'Muitas solicitações.', undefined, { 'Retry-After': '2' }),
    });
    await user.type(screen.getByLabelText('Novo e-mail'), 'beatriz@ufla.br');
    await user.type(screen.getAllByLabelText('Senha atual')[1], 'SenhaAtual123!');
    await user.click(screen.getByRole('button', { name: 'Enviar link de confirmação' }));

    expect((await screen.findByRole('alert')).textContent).toContain('Tente novamente em 2 segundos.');
    expect((screen.getByRole('button', { name: 'Enviar link de confirmação' }) as HTMLButtonElement).disabled).toBe(true);
    expect(calls.filter((call) => call.path === '/api/v1/usuarios/me/email')).toHaveLength(1);
  });

  it('não exibe a aba Dispositivos sem recurso correspondente na API', async () => {
    await openProfile();
    expect(screen.queryByRole('button', { name: 'Dispositivos' })).toBeNull();
    expect(screen.queryByText(/dispositivos associados/i)).toBeNull();
  });

  it('remove o token antes das chamadas e confirma uma única vez sob StrictMode', async () => {
    const { calls } = mockApi();
    setPath('/confirmar-troca-email?origem=mensagem#token=abc%2B123');
    render(<StrictMode><App/></StrictMode>);

    expect(await screen.findByText('E-mail alterado. Entre novamente com o novo endereço.')).toBeTruthy();
    const confirmationCalls = calls.filter((call) => call.path === '/api/v1/auth/confirmar-troca-email');
    expect(confirmationCalls).toHaveLength(1);
    expect(confirmationCalls[0].body).toEqual({ token: 'abc+123' });
    expect(calls.every((call) => call.hashAtRequest === '')).toBe(true);
    expect(window.location.pathname + window.location.search + window.location.hash).toBe('/confirmar-troca-email?origem=mensagem');
    expect(calls.some((call) => call.path === '/api/v1/auth/logout')).toBe(false);
    expect(screen.getByRole('link', { name: 'Entrar' })).toBeTruthy();
  });

  it('mantém a sessão encerrada se a consulta inicial terminar depois da confirmação', async () => {
    let resolveMe!: (result: Response) => void;
    const pendingMe = new Promise<Response>((resolve) => { resolveMe = resolve; });
    mockApi({ me: () => pendingMe });
    setPath('/confirmar-troca-email#token=token-de-troca');
    render(<App/>);

    expect(await screen.findByText('E-mail alterado. Entre novamente com o novo endereço.')).toBeTruthy();
    await act(async () => { resolveMe(response(200, userDto)); });
    await act(async () => {
      window.history.pushState(null, '', '/perfil');
      window.dispatchEvent(new PopStateEvent('popstate'));
    });
    expect(await screen.findByRole('heading', { name: 'Acesse sua pesquisa.' })).toBeTruthy();
  });

  it('orienta a solicitar novo link em Minha conta quando o token expirou', async () => {
    const { calls } = mockApi({
      confirmEmailChange: () => apiError(400, 'Link inválido ou expirado.'),
    });
    setPath('/confirmar-troca-email#token=expirado');
    render(<App/>);

    expect(await screen.findByRole('heading', { name: 'Link inválido ou expirado' })).toBeTruthy();
    expect(screen.getByText(/solicite a troca novamente em Minha conta/i)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Minha conta' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /reenviar/i })).toBeNull();
    expect(calls.filter((call) => call.path === '/api/v1/auth/confirmar-troca-email')).toHaveLength(1);
  });
});
