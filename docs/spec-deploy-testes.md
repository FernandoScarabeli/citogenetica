# Spec: ambiente de testes para o cliente (API no PC do dono, front no Netlify)

Escrita em 2026-11-13, depois da reunião de apresentação. Ponto de partida para um chat novo.

## Objetivo

O laboratório (cliente) quer testar o sistema por conta própria. Colocar no ar:

- **Front** (`apps/web`) no **Netlify**.
- **API, banco e armazenamento de fotos** (`apps/api`, Postgres, RustFS) no **PC do dono**, que vai servir de servidor.

É um ambiente de **testes do cliente**, não a produção definitiva da UFLA. Mesmo assim, o que o cliente cadastrar deve ser tratado como dado real: não pode se perder nem ficar exposto.

## Estado atual (verificado no código em 2026-11-13)

- `docker-compose.yml` sobe `api`, `web` (Nginx com o build do front), `db`, `rustfs` e `mailpit`. As variáveis vêm do `.env`, que é gerado por `scripts/gerar-env.sh`.
- **Portas abertas para a rede toda (`0.0.0.0`):**
  - API: `SERVER_PORT`
  - web: `WEB_PORT`
  - Postgres: `SPRING_DATASOURCE_PORT`, hoje 5432
  - RustFS: `STORAGE_PORT` (9000) e console em `STORAGE_CONSOLE_PORT`

  Só o Mailpit fica em `127.0.0.1`. **Isso não pode ir para o servidor assim.**
- **Autenticação:** cookie de sessão com token CSRF; as rotas de escrita exigem `csrf-token`. CORS é configurado em `API_CORS_ALLOWED_ORIGINS` (`config/security/CorsProperties.java`).
- **Proxy confiável:** `API_SECURITY_TRUSTED_PROXY_REGEX` vale, por padrão, o IP fixo do serviço `web` (`10.89.57.10`). Com ele, os limites por IP enxergam o IP real do cliente. Atrás de Netlify e túnel essa cadeia muda; ver pendências.
- **Fotos:** o front busca a imagem pela API (`/api/v1/laminas/{id}/fotos/{fotoId}/arquivo`). Existe também `STORAGE_PUBLIC_ENDPOINT` (URLs assinadas); falta conferir quem usa e se precisa ser público.
- **E-mail** (convites, recuperação de senha): vai para o Mailpit, que não entrega nada. `API_FRONTEND_URL` monta os links dos e-mails.

## Proposta (recomendação; o dono ainda não decidiu)

1. **Expor a API com Cloudflare Tunnel** (`cloudflared`), sem abrir porta no roteador. Ganha HTTPS e não mostra o IP de casa. Requer um domínio na Cloudflare (ex.: `api.<dominio>`). Sem domínio, só dá para usar endereço temporário (`trycloudflare.com`), que muda a cada reinício.
2. **Netlify encaminha `/api/*` para a API** (regra de redirecionamento com status 200 em `netlify.toml` ou `_redirects`), em vez de o front chamar outro domínio. Para o navegador tudo fica no mesmo site, e o cookie de sessão e o CSRF funcionam sem mexer em `SameSite` e CORS.
   - **Testar:** envio de foto de até 10 MB passando pelo proxy do Netlify (limite de corpo e tempo do proxy) e download de exportações.
   - Se o proxy não aguentar, a alternativa é o front chamar a API direto: `SameSite=None; Secure`, CORS com credenciais e CSRF entre domínios. É mais trabalho e mais risco.
   - Conferir o que o `apps/web/nginx.conf` faz hoje (encaminhamento de `/api`, cabeçalhos, rotas do SPA) e reproduzir no Netlify, inclusive o fallback de rotas para `index.html`.
3. **Compose próprio do servidor** (ex.: `docker-compose.servidor.yml` ou um override):
   - nenhuma porta publicada para fora, exceto a API, e só em `127.0.0.1`, que é onde o `cloudflared` conecta;
   - sem o serviço `web`, porque o front fica no Netlify;
   - sem o Mailpit, se o e-mail for real;
   - console do RustFS desligado ou só em `127.0.0.1`;
   - `restart: unless-stopped` e subida automática quando o PC liga (o Docker inicia com o sistema);
   - volumes próprios, separados dos de desenvolvimento do dono (outro nome de projeto no compose).
4. **Segredos novos** num `.env` do servidor, gerado pelo `scripts/gerar-env.sh` ou por uma variação dele: senha do banco, chaves do RustFS e qualquer segredo de sessão. Perfil Spring de produção, não o `dev`. Ajustar `API_FRONTEND_URL` e `API_CORS_ALLOWED_ORIGINS` para o domínio do Netlify.
5. **Backup:** `pg_dump` diário com rotação (ex.: manter 14) e cópia do volume do RustFS, gravados fora do volume Docker. Documentar como restaurar e testar a restauração uma vez.
6. **E-mail:** SMTP real (ex.: Gmail com senha de app) ou, por enquanto, o dono repassa os links de convite na mão. Se ficar sem e-mail, a interface não pode prometer que um e-mail foi enviado; ver "Honestidade da interface" em `apps/web/AGENTS.md`.
7. **Primeiro admin:** seguir a ADR `docs/adr/0002-bootstrap-primeiro-admin.md`.
8. **Passo a passo** no README (ou em `docs/deploy-testes.md`): instalar e registrar o `cloudflared` como serviço, subir o compose, configurar o Netlify (diretório base `apps/web`, comando `npm run build`, publicar `dist`, versão do Node do `.nvmrc`), rodar e restaurar backup, atualizar para uma versão nova.

## Perguntas para o dono antes de começar

1. Tem domínio, ou pode comprar um e colocar na Cloudflare?
2. E-mail real (qual conta SMTP) ou convites repassados na mão por enquanto?
3. Qual sistema roda no PC servidor, e ele fica ligado 24 horas?
4. Qual branch vai ao ar: `main` depois de mesclar `dev`, ou `dev`? O PR #105 (marcações na foto) precisa estar mesclado antes.
5. O cliente começa com a base vazia (só o admin) ou com algum dado importado?

## Pendências técnicas a investigar

- `API_SECURITY_TRUSTED_PROXY_REGEX` com a cadeia Netlify → Cloudflare → `cloudflared` → API: qual IP chega e quais cabeçalhos (`X-Forwarded-For`, `CF-Connecting-IP`) dá para confiar sem permitir forjar o IP. Sem isso, os limites de tentativa de login veem todo mundo como o mesmo IP.
- O cookie de sessão sai com `Secure` atrás de HTTPS terminado no proxy? (`X-Forwarded-Proto` e `server.forward-headers-strategy`).
- Quem usa `STORAGE_PUBLIC_ENDPOINT` e as URLs assinadas: se algum fluxo manda o navegador direto ao RustFS, o RustFS precisaria de endereço público. A preferência é fazer tudo passar pela API.
- Limites de upload: tamanho máximo do Spring e do proxy, e o tempo limite do Netlify.

## Pronto quando

- O cliente acessa o endereço do Netlify, faz login, cria lâmina, envia e anota foto e comenta, tudo por HTTPS.
- Do lado de fora, nenhuma porta do banco, do RustFS ou do console responde (conferir com um scan na rede local e pela internet).
- O backup roda sozinho e uma restauração foi testada.
- Desligar e religar o PC traz tudo de volta sem passo manual.
- O passo a passo está documentado no repositório.
