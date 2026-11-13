# Citogenética

## O que é

Sistema web para registrar e resumir contagens de células em lâminas de citogenética, incluindo análises de meiose e mitose e cálculo do Índice Mitótico. O projeto atende ao fluxo do Laboratório de Citogenética da UFLA.

## Status

O sistema está em desenvolvimento. A API inclui autenticação por sessão, gestão de usuários e laboratórios, convites, notificações em tempo real e armazenamento com URLs assinadas. A aplicação web usa a API para autenticação, laboratórios e notificações; lâminas e contagens de pesquisa continuam armazenadas localmente no navegador (`localStorage`).

O Docker Compose inicia a API, o banco, o armazenamento, o Mailpit e o frontend servido pelo Nginx. Para desenvolvimento, o frontend também pode ser iniciado separadamente com Vite.

## Estrutura do repositório

```text
apps/
  api/      API Java
  web/      aplicação React
docs/       referências e documentação
scripts/    scripts de apoio
```

Detalhes da [API](apps/api/README.md) e da [aplicação web](apps/web/README.md).

## Como rodar

Pré-requisitos: Docker com Compose. Para desenvolver fora do Docker, use Node.js 26 LTS para a aplicação web e Java 17 para a API. A versão principal do Node está em `apps/web/.nvmrc`.

Na raiz do repositório, gere as configurações locais e inicie os serviços:

```sh
./scripts/gerar-env.sh
docker compose up -d --build
```

O script gera um `.env` com segredos aleatórios e não sobrescreve um arquivo existente. Se o `.env` já existe e faltam variáveis novas, use `./scripts/gerar-env.sh --add-missing`: o script acrescenta apenas chaves ausentes, gera somente os segredos ausentes e preserva o conteúdo existente. `./scripts/gerar-env.sh --force` substitui o arquivo inteiro. O script não imprime valores de configuração. As portas padrão abaixo vêm do `.env.example`; elas podem ser alteradas no `.env`.

| Serviço | Endereço local |
| --- | --- |
| Site | <http://localhost:3000> |
| API | <http://localhost:8080> |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| Health da API | <http://localhost:8080/actuator/health> |
| Mailpit | <http://localhost:8025> |
| Console do RustFS | <http://localhost:9090> |

As portas padrão estão no `.env.example`: `WEB_PORT` publica o site, `SERVER_PORT` publica a API, e `MAILPIT_WEB_PORT` publica o Mailpit. No template, as URLs de confirmação de e-mail e convites (`API_FRONTEND_URL` e `API_FRONTEND_*_URL`) usam a origem do site. Se você já tem `.env`, `--add-missing` preserva os valores existentes: atualize essas URLs e `API_CORS_ALLOWED_ORIGINS` para acompanhar `WEB_PORT`. A mesma origem do site encaminha `/api/` para a API, mantendo cookies de sessão e CSRF no mesmo domínio.

O Compose usa o perfil `dev`, com cookie de sessão HttpOnly, `SameSite=Lax` e `Secure=false` para HTTP local. Em um ambiente com HTTPS, configure `API_SECURITY_SESSION_COOKIE_SECURE=true`. Por padrão, `API_SECURITY_TRUSTED_PROXY_REGEX` fica vazio e a API ignora cabeçalhos encaminhados para identificar o IP do cliente. Configure essa expressão somente para os endereços dos proxies confiáveis; o Nginx envia `X-Forwarded-*`, mas a API só os aceita quando o proxy está configurado como confiável. Com o valor padrão, limites baseados em IP veem o endereço interno do Nginx no Compose.

Para desenvolver o frontend com Vite enquanto a API e os serviços estão no Compose:

```sh
docker compose up -d
cd apps/web
npm ci
npm run dev
```

O Vite usa `API_PROXY_TARGET=http://localhost:8080` por padrão; se `SERVER_PORT` tiver outro valor, aponte `API_PROXY_TARGET` para essa porta. Os e-mails locais apontam para o site servido pelo Compose; para testar uma rota de e-mail diretamente no Vite, ajuste as variáveis `API_FRONTEND_*_URL` no `.env` para `http://localhost:5173` e reinicie a API. Para parar os serviços do Compose, execute na raiz:

```sh
docker compose down
```

`docker compose down -v` também apaga os dados locais do banco e do armazenamento de arquivos.

## Testes

Na aplicação web, em `apps/web`:

```sh
npm test
npm run build
```

Os testes da API iniciam PostgreSQL 16 e, no teste de integração de storage, RustFS isolados com Testcontainers. É necessário ter Docker disponível; não é necessário criar `.env`, iniciar o Compose ou serviços locais de RustFS/Mailpit, nem exportar variáveis do projeto:

```sh
cd apps/api
./mvnw test
```

## Licença

Licenciado sob AGPL-3.0-or-later: quem modificar o sistema e oferecê-lo por rede deve disponibilizar aos usuários o código-fonte correspondente. Consulte o arquivo [LICENSE](LICENSE).

## Autoria e manutenção

Mantido por Scarabeli.
