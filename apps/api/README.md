# Citogenetica — API

Esta pasta contém a API do sistema de registro de lâminas de citogenética da UFLA. O backend está em desenvolvimento; sua base atual inclui autenticação e gestão de usuários, notificações, armazenamento de arquivos com URLs temporárias assinadas e um CRUD de exemplo mantido como referência de implementação.

## Tecnologias

- Java 17
- Spring Boot 3.3.4
- Maven
- PostgreSQL e Flyway
- Spring Security, sessão JDBC, cookie HttpOnly e proteção CSRF
- SpringDoc OpenAPI (Swagger)
- Docker Compose
- RustFS com API compatível com S3
- Lombok e MapStruct

## Pré-requisitos

- Docker e Docker Compose
- Java 17 e Maven Wrapper para executar a API fora do Docker

## Configuração

Na raiz do repositório, gere um `.env` local com segredos aleatórios:

```bash
./scripts/gerar-env.sh
```

O script não sobrescreve um `.env` existente. Se faltarem variáveis novas, `./scripts/gerar-env.sh --add-missing` acrescenta somente as chaves ausentes, gera segredos apenas para as chaves ausentes e preserva o conteúdo existente; os valores não são impressos. `./scripts/gerar-env.sh --force` substitui o arquivo inteiro. O arquivo gerado recebe permissão restrita. O `.env.example` documenta as variáveis e usa marcadores nos segredos; copiar o exemplo diretamente sem gerar ou trocar os segredos faz a API recusar a inicialização.

Por padrão, cabeçalhos de IP encaminhado não são confiáveis. Em produção atrás de proxy, configure `API_SECURITY_TRUSTED_PROXY_REGEX` com uma expressão que corresponda somente aos proxies confiáveis; o Tomcat processa esses cabeçalhos. A API usa uma sessão armazenada no PostgreSQL, com cookie `CITOGENETICA_SESSION` HttpOnly, `SameSite=Lax` e `Secure` habilitado por padrão. O perfil local `dev` permite HTTP; configure `API_SECURITY_SESSION_TIMEOUT` para ajustar o tempo de inatividade.

As variáveis principais incluem `SERVER_PORT`, as configurações `SPRING_DATASOURCE_*`, as configurações de segurança e e-mail, `API_FRONTEND_URL`, `MAILPIT_WEB_PORT` e as configurações `STORAGE_*`. No Compose local, a API envia e-mails pelo Mailpit; a interface fica em `http://localhost:8025` por padrão. Para outro servidor SMTP, configure `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_AUTH` e `SPRING_MAIL_STARTTLS_ENABLE`. Com autenticação SMTP habilitada, STARTTLS também precisa estar habilitado.

`API_CORS_ALLOWED_ORIGINS` define, em uma lista separada por vírgulas, as origens exatas que podem acessar a API com credenciais. No exemplo local, a origem é `http://localhost:5173`, compatível com o Vite. `API_FRONTEND_URL` continua definindo a origem permitida pelo RustFS.

Como Vite e API têm origens diferentes, o SPA deve usar `credentials: 'include'` nas chamadas. Chame `GET /api/v1/auth/csrf` antes do login e antes de cada nova sessão após autenticar ou sair. O endpoint emite o cookie legível `XSRF-TOKEN`; em cada `POST`, `PUT`, `PATCH` ou `DELETE`, envie o valor atual no cabeçalho `X-XSRF-TOKEN`. Com `credentials: 'include'`, o navegador envia automaticamente o cookie de sessão HttpOnly. O login retorna `204` e estabelece a sessão pelo cookie.

## Executando com Docker Compose

Execute na raiz do repositório:

```bash
docker compose up -d --build
```

Serviços locais:

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- RustFS API S3: `http://localhost:9000`
- RustFS Console: `http://localhost:9090`

## Executando a API localmente

Suba PostgreSQL e RustFS usando os serviços do Compose:

```bash
docker compose up -d db rustfs
```

Carregue explicitamente no shell as variáveis do `.env` da raiz e inicie a aplicação. A API não lê arquivos `.env` automaticamente:

```bash
cd apps/api
set -a; source ../../.env; set +a
./mvnw spring-boot:run
```

Para depurar pela IDE, configure a exportação das variáveis do `.env` da raiz na configuração de execução e mantenha o diretório de trabalho em `apps/api/`. Os testes da API não dependem dessas variáveis: `./mvnw test` usa propriedades isoladas e o PostgreSQL do Testcontainers.

## Estrutura

```text
src/
  main/
    java/br/ufla/citogenetica/
    resources/
      application.yml
      db/migration/
  test/java/br/ufla/citogenetica/
```

A classe principal é `CitogeneticaApplication`. O projeto segue atualmente uma organização em camadas; a evolução para o monólito modular planejado ocorrerá nas etapas próprias do projeto.

## Documentação da API

- Swagger UI: `http://localhost:8080/swagger-ui.html`
