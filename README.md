# Citogenética

## O que é

Sistema web para registrar e resumir contagens de células em lâminas de citogenética, incluindo análises de meiose e mitose e cálculo do Índice Mitótico. O projeto atende ao fluxo do Laboratório de Citogenética da UFLA.

## Status

O projeto está em desenvolvimento inicial. A API já inclui login, recuperação de senha, gestão de usuários e notificações, um serviço interno de armazenamento com URLs assinadas e um CRUD de exemplo. Ainda não há auto-cadastro disponível: o endpoint de registro é restrito a administradores e não existe uma conta administradora inicial; auto-cadastro com confirmação de e-mail é a próxima etapa.

A aplicação web permite registrar lâminas e simula o fluxo de laboratórios, mas guarda os dados no navegador (`localStorage`) e ainda não se comunica com a API. Ela também não está no Docker Compose e deve ser iniciada separadamente com Vite.

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

Pré-requisitos: Docker com Compose. Para desenvolver fora do Docker, use Node.js 22 para a aplicação web e Java 17 para a API.

Na raiz do repositório, gere as configurações locais e inicie os serviços:

```sh
./scripts/gerar-env.sh
docker compose up -d --build
```

O script gera um `.env` com segredos aleatórios e não sobrescreve um arquivo existente. As portas padrão abaixo vêm do `.env.example`; elas podem ser alteradas no `.env`.

| Serviço | Endereço local |
| --- | --- |
| API | <http://localhost:8080> |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| Health da API | <http://localhost:8080/actuator/health> |
| Mailpit | <http://localhost:8025> |
| Console do RustFS | <http://localhost:9090> |

Para iniciar a aplicação web em outro terminal:

```sh
cd apps/web
npm install
npm run dev
```

Abra o endereço informado pelo Vite. Para parar os serviços do Compose, execute na raiz:

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
