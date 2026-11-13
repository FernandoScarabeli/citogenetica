# Ambiente de testes do cliente

O front fica no **Netlify**. A API, o banco e as fotos rodam num **PC servidor**, atrás de um túnel temporário da Cloudflare (`*.trycloudflare.com`). Não há domínio próprio e nenhuma porta do PC fica aberta.

```
navegador ──HTTPS──> Netlify (front + proxy de /api/*) ──HTTPS──> túnel Cloudflare ──> cloudflared ──> api ──> db, rustfs
```

- Para o navegador, tudo fica no mesmo site (`<site>.netlify.app`). Por isso o cookie de sessão e o CSRF funcionam sem CORS.
- O endereço do túnel **muda a cada reinício** do `cloudflared`. O serviço `netlify-sync` percebe a mudança, grava o novo endereço na variável `API_ORIGIN` do site no Netlify e dispara um build. Do lado do cliente, o endereço do Netlify nunca muda. Depois de um reinício, o site leva **1 a 2 minutos** (o tempo do build) para voltar a falar com a API.
- O túnel temporário não suporta SSE. Nesse build, as notificações são consultadas a cada 30 s (`VITE_NOTIFICACOES_POR_CONSULTA`, definido no `netlify.toml`).

Arquivos: `netlify.toml`, `deploy/netlify/` e `deploy/servidor/`.

## 1. Preparar o PC servidor

1. Instalar Docker Engine com o plugin Compose e deixar o Docker subir com o sistema (`sudo systemctl enable --now docker` no Linux).
2. Clonar o repositório e ficar na `main`.
3. Desligar a suspensão automática. O PC precisa ficar ligado 24 horas.

## 2. Criar o site no Netlify

1. Em *Add new project → Import an existing project*, escolher o repositório no GitHub, branch `main`. O `netlify.toml` já define a base (`apps/web`), o comando e a pasta publicada.
2. **O primeiro build vai falhar**, porque ainda não existe `API_ORIGIN`. Isso é esperado: o `netlify-sync` cria a variável e dispara o build certo no passo 3.
3. Anotar:
   - o endereço do site (`https://<nome>.netlify.app`; dá para trocar o nome em *Site configuration → Change site name*);
   - o **Site ID** (*Site configuration → General*);
   - um **Personal access token** (*User settings → Applications → New access token*).

## 3. Subir o servidor

```sh
deploy/servidor/gerar-env.sh https://<nome>.netlify.app
# edite deploy/servidor/.env e preencha os campos "preencher":
#   SPRING_MAIL_USERNAME e API_EMAIL_FROM: a conta Gmail
#   SPRING_MAIL_PASSWORD: a senha de app do Gmail (myaccount.google.com/apppasswords)
#   NETLIFY_AUTH_TOKEN e NETLIFY_SITE_ID
cd deploy/servidor
docker compose up -d --build
docker compose logs -f netlify-sync   # espere "túnel em https://...; build do Netlify disparado"
```

O `.env` é criado com permissão 600 e senhas aleatórias. **Não o versione e não o apague**: sem ele, o banco e as fotos ficam inacessíveis.

## 4. Primeiro administrador (ADR 0002)

1. Crie a conta pelo site e confirme o e-mail.
2. No servidor, rode:

```sh
cd deploy/servidor
docker compose run --rm --no-deps api --run-bootstrap-admin --app.bootstrap-admin.email=<email-da-conta>
```

O comando só funciona uma vez.

## 5. Backup e restauração

O serviço `backup` grava todo dia às `BACKUP_HORA` (UTC; o padrão 06 equivale a 03h em Brasília) em `BACKUP_DIR` (padrão `deploy/servidor/backups/`):

- `banco-AAAA-MM-DD.dump`: `pg_dump` em formato custom;
- `fotos-AAAA-MM-DD.tar.gz`: o volume do RustFS.

Ele guarda os `BACKUP_MANTER` mais recentes (padrão 14) e também faz um backup ao subir, se ainda não houver o do dia. Os arquivos são do root, com permissão 600. Para ter uma cópia fora do PC, copie a pasta com `sudo` para outro disco ou para a nuvem.

Para restaurar, o script apaga o estado atual e pede confirmação:

```sh
deploy/servidor/restaurar.sh AAAA-MM-DD
```

## 6. Atualizar para uma versão nova

```sh
git pull                       # na main
cd deploy/servidor
docker compose up -d --build   # reconstrói a API; o Flyway aplica as migrations
```

O Netlify publica o front sozinho a cada push na `main`.

## 7. Conferências

- `docker compose ps`: todos `Up`, sem nenhuma porta publicada.
- `docker compose logs cloudflared | grep trycloudflare` mostra o endereço atual do túnel.
- Pelo site, fazer login, criar uma lâmina e enviar uma foto de ~10 MB. Esse envio passa pelo proxy do Netlify, que corta em 26 s.

## Limitações conhecidas (aceitas para os testes)

- **Disponibilidade:** o túnel temporário não tem garantia de funcionamento e aceita até 200 requisições simultâneas.
- **Limites por IP** (login, cadastro etc.): a API vê o IP de saída do Netlify, não o do usuário, porque só confia no `cloudflared`. Os limites por e-mail continuam valendo normalmente.
- **API direta:** o endereço do túnel também responde diretamente, sem passar pelo Netlify. Valem as mesmas regras de sessão, CSRF e CORS (só a origem do Netlify é aceita); o Swagger fica desligado.
- **Para virar produção:** domínio próprio com túnel nomeado da Cloudflare. Com isso o endereço fica fixo, o `netlify-sync` deixa de ser necessário e o SSE volta a funcionar.
