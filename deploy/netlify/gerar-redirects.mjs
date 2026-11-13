// Roda no build do Netlify (depois do vite build) e grava dist/_redirects encaminhando /api/*
// para a API em API_ORIGIN (o túnel do servidor de testes). Para o navegador fica tudo no mesmo
// site, então o cookie de sessão e o CSRF funcionam sem CORS. A regra do SPA fica no netlify.toml,
// que o Netlify avalia depois do _redirects.
import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const origem = (process.env.API_ORIGIN ?? '').trim().replace(/\/+$/, '');
let url;
try {
  url = new URL(origem);
} catch {
  url = null;
}
if (!url || url.protocol !== 'https:' || url.pathname !== '/' || url.search || url.hash) {
  console.error('API_ORIGIN ausente ou inválida (esperado https://<host>, sem caminho). Build interrompido.');
  process.exit(1);
}

const destino = resolve(process.argv[2] ?? 'dist', '_redirects');
writeFileSync(destino, `/api/*  ${url.origin}/api/:splat  200!\n`);
console.log(`_redirects: /api/* -> ${url.origin}/api/*`);
