import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

afterEach(() => {
  cleanup();
  document.cookie = 'XSRF-TOKEN=; Path=/; Max-Age=0';
  localStorage.clear();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

// Fotos e comentários da lâmina: sem servidor nos testes, as listas começam vazias.
// Os testes próprios desses blocos passam uma API falsa pelo parâmetro `api`.
vi.mock('../src/api/colaboracao-api', async importOriginal => {
  const original = await importOriginal<typeof import('../src/api/colaboracao-api')>();
  return {
    ...original,
    colaboracaoApi: {
      listarFotos: async () => [],
      enviarFoto: async () => { throw new Error('sem servidor nos testes'); },
      apagarFoto: async () => undefined,
      listarComentarios: async () => [],
      comentar: async () => { throw new Error('sem servidor nos testes'); },
      apagarComentario: async () => undefined,
    },
  };
});
