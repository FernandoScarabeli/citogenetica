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

## Primeiro administrador

O primeiro administrador global é promovido uma única vez por um comando administrativo. Primeiro, a pessoa deve criar a conta pelo cadastro normal e confirmar o e-mail recebido. Depois, quem opera o deploy executa o comando com o e-mail confirmado. O processo não abre servidor web, não cria sessão e termina com código `0` em caso de sucesso ou diferente de zero em caso de falha. A conta precisa existir, estar ativa e ter o e-mail confirmado.

Com o JAR já empacotado, PostgreSQL e RustFS acessíveis e as variáveis do `.env` exportadas:

```bash
cd apps/api
set -a; source ../../.env; set +a
java -jar target/citogenetica-api-0.0.1-SNAPSHOT.jar \
  --run-bootstrap-admin \
  --app.bootstrap-admin.email=usuario@exemplo.org
```

Com Docker Compose, inicie antes o banco e o RustFS. O RustFS é necessário porque a inicialização atual da aplicação verifica o bucket de arquivos. O serviço `api` recebe as configurações do `.env`; `docker compose run` não publica a porta HTTP por padrão.

```bash
docker compose up -d --wait db rustfs
docker compose run --rm --no-deps api \
  --run-bootstrap-admin \
  --app.bootstrap-admin.email=usuario@exemplo.org
```

O comando só deve ser executado depois que a migração do banco estiver aplicada. A tabela `bootstrap_admin_plataforma` conserva o consumo mesmo se o administrador for removido depois; uma segunda execução falha e não promove outra conta. A promoção revoga as sessões existentes do usuário, que deverá entrar novamente para receber o novo papel.

### Recuperação excepcional

Se todos os administradores forem removidos depois que o marcador de uso único tiver sido consumido, interrompa as instâncias da API, faça backup do banco e siga o procedimento institucional de verificação de identidade fora de banda. Um DBA deve confirmar que não há outro administrador ativo, escolher uma conta `ATIVO` com e-mail confirmado e executar a alteração diretamente no PostgreSQL, em transação, atualizando exatamente um usuário para o papel `ADMIN` e removendo suas linhas de `spring_session` (as linhas dependentes em `spring_session_attributes` são removidas pela FK). Confirme o número de linhas alteradas antes do `COMMIT`; em qualquer divergência, faça `ROLLBACK`. Registre a intervenção no processo de auditoria operacional. **Não apague, recrie nem desconsuma** a linha de `bootstrap_admin_plataforma`: ela é o registro histórico de que o comando já foi usado. Depois, valide o acesso e reative a API.

Exemplo de sequência para um cliente `psql` (defina `usuario_id` somente após a verificação fora de banda; não execute o `COMMIT` até validar que o `UPDATE` alterou exatamente uma linha):

```sql
\set usuario_id 123
BEGIN;
SELECT u.id, u.status, u.email_confirmado_em, r.nome
FROM usuario u JOIN role r ON r.id = u.role_id
WHERE u.id = :usuario_id
FOR UPDATE OF u;
UPDATE usuario
SET role_id = (SELECT id FROM role WHERE nome = 'ADMIN'),
    version = version + 1,
    last_modified_date = NOW()
WHERE id = :usuario_id
  AND status = 'ATIVO'
  AND email_confirmado_em IS NOT NULL;
DELETE FROM spring_session WHERE principal_name = :'usuario_id';
-- Verifique que o UPDATE alterou uma linha e que a conta resultante tem role ADMIN.
COMMIT;
```

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
