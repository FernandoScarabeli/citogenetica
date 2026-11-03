import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { transformWithOxc } from 'vite';

const sourceRoot = new URL('../src/', import.meta.url);
const compile = async (path) => {
  const source = readFileSync(new URL(path, sourceRoot), 'utf8');
  const { code } = await transformWithOxc(source, path);
  return code;
};
const toModuleUrl = (source) => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`;

const errorsUrl = toModuleUrl(await compile('api/errors.ts'));
const httpSource = (await compile('api/http-client.ts')).replace(/from ["']\.\/errors\.js["']/, `from '${errorsUrl}'`);
const httpUrl = toModuleUrl(httpSource);
const authContractsUrl = toModuleUrl(await compile('api/contracts/auth.ts'));
const authSource = (await compile('api/auth-api.ts'))
  .replace(/from ["']\.\/contracts\/auth\.js["']/, `from '${authContractsUrl}'`)
  .replace(/from ["']\.\/http-client\.js["']/, `from '${httpUrl}'`);
const authUrl = toModuleUrl(authSource);
const { createHttpClient } = await import(httpUrl);
const {
  ApiError,
  NetworkError,
  isConflict,
  isForbidden,
  isNotFound,
  isRateLimited,
  isUnauthorized,
} = await import(errorsUrl);
const { createAuthApi } = await import(authUrl);

function setCookie(value = '') {
  Object.defineProperty(globalThis, 'document', {
    configurable: true,
    value: { cookie: value },
  });
}

function clearCookie() {
  delete globalThis.document;
}

function response(status, body, headers = {}) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: body === undefined ? headers : { 'content-type': 'application/json', ...headers },
  });
}

function csrfFailure(path = '/api/recurso') {
  return response(403, {
    status: 403,
    error: 'Forbidden',
    message: 'Token CSRF ausente ou inválido.',
    path,
    details: null,
  });
}

test('busca CSRF uma vez, envia o token nas mutações e não o envia em GET', async () => {
  setCookie();
  const calls = [];
  const client = createHttpClient({
    fetch: async (path, init) => {
      calls.push({ path, init });
      if (path === '/api/v1/auth/csrf') {
        document.cookie = 'XSRF-TOKEN=token%20inicial';
        return response(204);
      }
      return response(204);
    },
  });

  try {
    await client.request('/api/leitura');
    assert.equal(calls.length, 1);
    assert.equal(calls[0].init.headers.get('X-XSRF-TOKEN'), null);

    for (const method of ['POST', 'PUT', 'PATCH', 'DELETE']) {
      await client.request('/api/escrita', { method, json: { ativo: true } });
    }

    assert.equal(calls.filter(({ path }) => path === '/api/v1/auth/csrf').length, 1);
    for (const call of calls.filter(({ path }) => path === '/api/escrita')) {
      assert.equal(call.init.credentials, 'include');
      assert.equal(call.init.headers.get('X-XSRF-TOKEN'), 'token inicial');
      assert.equal(call.init.headers.get('Content-Type'), 'application/json');
    }
  } finally {
    clearCookie();
  }
});

test('login e logout renovam o token CSRF depois da resposta', async () => {
  setCookie();
  const calls = [];
  let tokenVersion = 0;
  const client = createHttpClient({
    fetch: async (path, init) => {
      calls.push({ path, init });
      if (path === '/api/v1/auth/csrf') {
        tokenVersion += 1;
        document.cookie = `XSRF-TOKEN=token-${tokenVersion}`;
        return response(204);
      }
      return response(204);
    },
  });

  try {
    const auth = createAuthApi(client);
    await auth.login({ email: 'ana@example.com', senha: 'senha' });
    await auth.logout();

    assert.deepEqual(calls.map(({ path }) => path), [
      '/api/v1/auth/csrf',
      '/api/v1/auth/login',
      '/api/v1/auth/csrf',
      '/api/v1/auth/logout',
      '/api/v1/auth/csrf',
    ]);
    assert.equal(calls[1].init.headers.get('X-XSRF-TOKEN'), 'token-1');
    assert.equal(calls[3].init.headers.get('X-XSRF-TOKEN'), 'token-2');
    assert.equal(tokenVersion, 3);
  } finally {
    clearCookie();
  }
});

test('repete uma mutação uma única vez após rejeição CSRF identificada', async () => {
  setCookie();
  const calls = [];
  let csrfVersion = 0;
  let mutationCount = 0;
  const client = createHttpClient({
    fetch: async (path, init) => {
      calls.push({ path, init });
      if (path === '/api/v1/auth/csrf') {
        csrfVersion += 1;
        document.cookie = `XSRF-TOKEN=token-${csrfVersion}`;
        return response(204);
      }
      mutationCount += 1;
      return mutationCount === 1 ? csrfFailure(path) : response(204);
    },
  });

  try {
    await client.request('/api/recurso', { method: 'POST', json: { valor: 1 } });
    const mutations = calls.filter(({ path }) => path === '/api/recurso');
    assert.equal(mutations.length, 2);
    assert.deepEqual(mutations.map(({ init }) => init.headers.get('X-XSRF-TOKEN')), ['token-1', 'token-2']);
    assert.equal(mutations[0].init.body, mutations[1].init.body);
    assert.equal(calls.filter(({ path }) => path === '/api/v1/auth/csrf').length, 2);
  } finally {
    clearCookie();
  }
});

test('não repete 403 de permissão', async () => {
  setCookie();
  const calls = [];
  const client = createHttpClient({
    fetch: async (path, init) => {
      calls.push({ path, init });
      if (path === '/api/v1/auth/csrf') {
        document.cookie = 'XSRF-TOKEN=token-1';
        return response(204);
      }
      return response(403, {
        status: 403,
        error: 'Forbidden',
        message: 'Acesso negado. Você não possui permissão para este recurso.',
        path,
      });
    },
  });

  try {
    await assert.rejects(
      client.request('/api/admin', { method: 'POST' }),
      (error) => error instanceof ApiError && isForbidden(error),
    );
    assert.equal(calls.filter(({ path }) => path === '/api/admin').length, 1);
    assert.equal(calls.filter(({ path }) => path === '/api/v1/auth/csrf').length, 1);
  } finally {
    clearCookie();
  }
});

test('trata resposta 204 sem tentar ler corpo', async () => {
  setCookie('XSRF-TOKEN=token');
  const client = createHttpClient({ fetch: async () => response(204) });
  try {
    assert.equal(await client.request('/api/sem-corpo'), undefined);
  } finally {
    clearCookie();
  }
});

test('cria ApiError com JSON e tolera erro sem corpo ou com corpo não JSON', async () => {
  setCookie('XSRF-TOKEN=token');
  let currentResponse = response(400, {
    timestamp: '2026-11-03T10:00:00',
    status: 400,
    error: 'Bad Request',
    message: 'Erro de validação',
    path: '/api/usuarios',
    details: ['nome: Nome obrigatório'],
  });
  const client = createHttpClient({ fetch: async () => currentResponse });

  try {
    await assert.rejects(client.request('/api/usuarios'), (error) => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 400);
      assert.equal(error.error, 'Bad Request');
      assert.equal(error.message, 'Erro de validação');
      assert.equal(error.path, '/api/usuarios');
      assert.equal(error.timestamp, '2026-11-03T10:00:00');
      return true;
    });

    currentResponse = new Response('<h1>Erro</h1>', {
      status: 502,
      headers: { 'content-type': 'text/html' },
    });
    await assert.rejects(client.request('/api/indisponivel'), (error) => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 502);
      assert.equal(error.path, '/api/indisponivel');
      assert.equal(error.message, 'Não foi possível concluir a solicitação.');
      return true;
    });

    currentResponse = response(503);
    await assert.rejects(client.request('/api/sem-corpo'), (error) => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 503);
      return true;
    });
  } finally {
    clearCookie();
  }
});

test('mapeia detalhes de validação por campo e preserva múltiplas mensagens', () => {
  const error = new ApiError({
    status: 400,
    error: 'Bad Request',
    message: 'Erro de validação',
    path: '/api/usuarios',
    details: ['nome: obrigatório', 'nome: muito curto', 'email: inválido', 'erro geral'],
  });
  assert.deepEqual(error.fieldErrors, {
    nome: ['obrigatório', 'muito curto'],
    email: ['inválido'],
  });
});

test('lê Retry-After em segundos e em data HTTP para respostas 429', async () => {
  setCookie('XSRF-TOKEN=token');
  let currentResponse = response(429, undefined, { 'retry-after': '37' });
  const client = createHttpClient({ fetch: async () => currentResponse });

  try {
    await assert.rejects(client.request('/api/limite'), (error) => {
      assert.ok(isRateLimited(error));
      assert.equal(error.retryAfterSeconds, 37);
      return true;
    });

    currentResponse = response(429, undefined, {
      'retry-after': new Date(Date.now() + 5000).toUTCString(),
    });
    await assert.rejects(client.request('/api/limite'), (error) => {
      assert.ok(isRateLimited(error));
      assert.ok(error.retryAfterSeconds >= 4 && error.retryAfterSeconds <= 5);
      return true;
    });
  } finally {
    clearCookie();
  }
});

test('401 aciona o assinante e os helpers identificam os status HTTP', async () => {
  setCookie('XSRF-TOKEN=token');
  const client = createHttpClient({
    fetch: async () => response(401, {
      status: 401,
      error: 'Unauthorized',
      message: 'Sessão encerrada.',
      path: '/api/privado',
    }),
  });
  let notifications = 0;
  const unsubscribe = client.onUnauthorized(() => { notifications += 1; });

  try {
    await assert.rejects(client.request('/api/privado'), (error) => isUnauthorized(error));
    assert.equal(notifications, 1);
    unsubscribe();
    assert.ok(isNotFound(new ApiError({ status: 404, error: 'Not Found', message: '', path: '' })));
    assert.ok(isConflict(new ApiError({ status: 409, error: 'Conflict', message: '', path: '' })));
  } finally {
    unsubscribe();
    clearCookie();
  }
});

test('separa falha de rede de abort solicitado pelo chamador', async () => {
  setCookie('XSRF-TOKEN=token');
  const networkClient = createHttpClient({ fetch: async () => { throw new TypeError('offline'); } });
  const controller = new AbortController();
  controller.abort();
  const abortClient = createHttpClient({
    fetch: async (_path, init) => {
      assert.equal(init.signal, controller.signal);
      throw new DOMException('A solicitação foi cancelada.', 'AbortError');
    },
  });

  try {
    await assert.rejects(networkClient.request('/api/rede'), (error) => {
      assert.ok(error instanceof NetworkError);
      assert.match(error.message, /Verifique sua conexão/);
      return true;
    });
    await assert.rejects(
      abortClient.request('/api/abortada', { signal: controller.signal }),
      (error) => error.name === 'AbortError' && !(error instanceof NetworkError),
    );
  } finally {
    clearCookie();
  }
});

test('nenhum módulo fora de src/api chama fetch diretamente', () => {
  function listFiles(directory) {
    return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
      const path = new URL(entry.name, `${directory}/`);
      return entry.isDirectory() ? listFiles(path) : [path];
    });
  }

  const violations = listFiles(sourceRoot)
    .filter((path) => !path.pathname.includes('/api/'))
    .filter((path) => /\bfetch\s*\(/.test(readFileSync(path, 'utf8')))
    .map((path) => path.pathname);
  assert.deepEqual(violations, []);
});
