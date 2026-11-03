import { defineConfig } from 'vitest/config';
import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Lido só pelo servidor de desenvolvimento (Node): sem prefixo VITE_, nunca entra no código do navegador.
const apiProxyTarget = process.env.API_PROXY_TARGET || 'http://localhost:8080';

export default defineConfig(() => {
  return {
    plugins: [react(), tailwindcss()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      proxy: {
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true,
          ws: false,
          configure(proxy) {
            proxy.on('proxyRes', (response) => {
              const contentType = response.headers['content-type'];
              if (typeof contentType === 'string' && contentType.startsWith('text/event-stream')) {
                // O proxy transmite SSE como stream; estes cabeçalhos evitam buffering intermediário.
                response.headers['cache-control'] = 'no-cache, no-transform';
                response.headers['x-accel-buffering'] = 'no';
              }
            });
          },
        },
      },
    },
    test: {
      environment: 'jsdom',
      include: ['tests/**/*.test.tsx'],
      setupFiles: ['./tests/setup.ts'],
      restoreMocks: true,
      clearMocks: true,
    },
  };
});
