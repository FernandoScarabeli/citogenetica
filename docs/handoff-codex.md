# Passagem para o Codex — onde estamos e o que vem agora

> Escrito em 2026-11-03, ao fim da Etapa 1 (back e front). Leia este documento inteiro antes de mexer em qualquer coisa.
> **Se algo aqui divergir do código, o código vence** — confira antes de afirmar.
> Documento anterior (histórico da coordenação, ainda útil para detalhes do back): `docs/handoff-coordenacao.md`.

---

## Sumário

1. [O projeto em um minuto](#1-o-projeto-em-um-minuto)
2. [Regras do dono do projeto (obrigatórias)](#2-regras-do-dono-do-projeto-obrigatórias)
3. [Estado atual — o que está pronto](#3-estado-atual--o-que-está-pronto)
4. [O que ainda é local no navegador](#4-o-que-ainda-é-local-no-navegador)
5. [Trabalho em aberto agora](#5-trabalho-em-aberto-agora)
6. [Lista de tarefas (ordem recomendada)](#6-lista-de-tarefas-ordem-recomendada)
7. [Etapa 2 — Contagem na API (detalhamento)](#7-etapa-2--contagem-na-api-detalhamento)
8. [Arquitetura e contratos que você precisa respeitar](#8-arquitetura-e-contratos-que-você-precisa-respeitar)
9. [Como rodar, testar e verificar](#9-como-rodar-testar-e-verificar)
10. [Armadilhas conhecidas deste ambiente](#10-armadilhas-conhecidas-deste-ambiente)
11. [Checklist de revisão (erros que já aconteceram)](#11-checklist-de-revisão-erros-que-já-aconteceram)
12. [Decisões de produto e perguntas em aberto](#12-decisões-de-produto-e-perguntas-em-aberto)
13. [Mapa de arquivos](#13-mapa-de-arquivos)

---

## 1. O projeto em um minuto

- **O que é:** sistema web para registrar e resumir contagens de células em lâminas de citogenética (meiose e mitose, Índice Mitótico) do Laboratório de Citogenética da UFLA. Substitui os protótipos HTML locais (`docs/registro-*.html`).
- **Repositório público:** `github.com/FernandoScarabeli/citogenetica`, licença AGPL-3.0-or-later.
- **Monorepo:**
  - `apps/api` — Java 17, Spring Boot 3.3.4, Maven, PostgreSQL 16 + Flyway, Spring Session JDBC (cookie HttpOnly + CSRF), RustFS (S3), Mailpit em dev. **Monólito modular** verificado por ArchUnit.
  - `apps/web` — React 19 + TypeScript + Vite + Tailwind v4 + React Router 8 + lucide-react. Testes: `node --test` (lógica pura) + Vitest/Testing Library (telas).
  - Nginx serve o site e faz proxy de `/api` para a API (mesma origem).
- **Subir tudo:** `./scripts/gerar-env.sh && docker compose up -d --build` → site em `http://localhost:3000`, e-mails de teste (Mailpit) em `http://localhost:8025`, API em `:8080` (Swagger em `/swagger-ui.html`, contrato em `/v3/api-docs`).

---

## 2. Regras do dono do projeto (obrigatórias)

### 2.1 Comunicação com o dono
- **Português do Brasil, curto e simples, sem jargão.** Primeiro o resultado; detalhe técnico só se ele pedir. Ele já reclamou de respostas técnicas demais.
- No fim de cada lote de trabalho: **uma listinha curta** do que foi feito e do que ele precisa fazer (normalmente: aprovar/mesclar PRs na ordem).

### 2.2 Qualidade
- **Sempre a melhor opção, nada de gambiarra.** Base segura e bem arquitetada desde o começo. Se é barato agora e caro depois, faça agora.
- Causa raiz, Clean Code, testes primeiro, diff mínimo, nada fora do escopo (só avisar no final).
- **Sem dependência nova** sem justificativa forte — e, se for estrutural, pergunte ao dono antes.
- **Migration do Flyway já commitada NUNCA é editada.** Mudança = migration nova (próxima livre hoje: **V12**).
- Comentários, mensagens de erro, textos de tela e documentação **em pt-BR**.

### 2.3 Autonomia
- O dono **não quer aprovar passo a passo**. Faça → teste → commite → abra PR → siga para a próxima tarefa. No fim, uma lista curta para ele aprovar.
- **Pare só para decisões que são dele:** comportamento de produto novo, custo, ações destrutivas. Escolhas técnicas são suas (escolha a melhor e explique em uma linha).
- **Mesclar PR só quando ele disser explicitamente** ("pode mergear", "vai na fé"). Abrir PR, sim; mesclar, não.

### 2.4 Git
- `main` e `dev` são protegidas (PR obrigatório + checks `API CI` e `Web CI`). **Nunca commite direto em `main` nem em `dev`.**
- Toda mudança: branch a partir de `dev` (`feat/`, `fix/`, `test/`, `docs/`, `chore/`, `refactor/`) → PR para `dev`.
- Se o PR anterior ainda não foi mesclado e o novo depende dele, **empilhe**: a branch nova sai da anterior e o PR aponta para ela.
- **Commits:** autor `Scarabeli`, Conventional Commits em pt-BR.
- **NUNCA** adicione `Co-Authored-By`, "Generated with…" ou qualquer atribuição a IA em commit, PR ou documentação. O dono proibiu explicitamente.
- **Não credite terceiros como autores** em README/TODO/docs.
- **Pegadinha da pilha de PRs:** `gh pr merge --delete-branch` num PR de base **fecha** o PR filho. Para mesclar uma pilha: mescle o primeiro sem apagar a branch; mude a base do filho para `dev` com `gh api -X PATCH repos/FernandoScarabeli/citogenetica/pulls/N -f base=dev` (`gh pr edit --base` falha por erro de "Projects classic"); feche e reabra o PR para disparar o CI; espere o CI; mescle; repita.
- O CI da API às vezes falha por rede ao baixar do Maven Central dentro do build da imagem Docker ("Non-resolvable parent POM ... absent"). Não é erro de código: `gh run rerun <id> --failed`.

---

## 3. Estado atual — o que está pronto

Tudo abaixo está **mesclado em `dev`** e foi testado de ponta a ponta com a API real e o Mailpit.

### 3.1 Back-end (API) — Etapa 1 completa
| Área | O que faz |
|---|---|
| Autenticação | Sessão por cookie (`CITOGENETICA_SESSION`, HttpOnly) + CSRF; login/logout; rate limit em Postgres com `429 + Retry-After`. |
| Cadastro | Auto-cadastro com confirmação de e-mail (link `#token=`), política de senha (8–64, senhas comuns bloqueadas, guarda de 72 bytes do BCrypt), respostas neutras (não revelam se o e-mail existe). |
| Recuperação de senha | Código de 6 dígitos com HMAC e tentativas limitadas; redefinir encerra as sessões. |
| Minha conta | Editar nome; trocar senha (409 se a atual estiver errada); trocar e-mail com confirmação no novo endereço (confirmar encerra todas as sessões). |
| Primeiro admin da plataforma | Comando de uso único (ADR 0002): `docker compose run --rm --no-deps api --run-bootstrap-admin --app.bootstrap-admin.email=pessoa@exemplo.org`. |
| Laboratórios | Criar (criador vira MEMBRO+ADMIN), listar os meus, renomear, membros (todos veem nome/e-mail/papéis), alterar papéis com `versaoEsperada`, remover, sair; regra do último admin (trigger adiada no banco); admin da plataforma desativa/reativa. |
| Convites | Admin convida por e-mail com papéis extras; reenviar invalida o anterior; revogar; aceite/recusa logado com e-mail confirmado igual ao destino; token só como SHA-256; link inválido = `400` genérico. |
| Orientação | Orientador convida; o convite também coloca a pessoa no laboratório; coorientação; encerrar = orientador ou admin; saída/remoção encerra vínculos; não dá para tirar ORIENTADOR com vínculo ativo (409). |
| Regra de acesso para a Etapa 2 | `laboratorio.api.LaboratorioAccessQuery`: **ver** dados do pesquisador = ele mesmo + orientadores com vínculo ativo + admins do laboratório (admins continuam vendo quem já saiu); **editar** = só o dono, participante ativo, laboratório ativo. **Use isso nas lâminas.** |
| Notificações | Por dono, paginadas com filtros, marcar lida/todas, SSE por usuário (`/api/events/subscribe`) após o commit; convites já geram notificação. |
| Arquitetura | Módulos `config`, `shared`, `infra`, `usuario`, `notificacao`, `laboratorio`, `exemplo`, com ArchUnit. |

Migrations: V1 base · V2 recuperação + rate limit · V3 Spring Session · V4 dono da notificação · V5 `unaccent` · V6 confirmação de e-mail · V7 bootstrap do admin · V8 troca de e-mail · V9 laboratório/participação · V10 convites · V11 orientação. **Próxima livre: V12.**

### 3.2 Front-end — integrado à API
| Tarefa | Situação |
|---|---|
| F01 cliente da API (`src/api/http-client.ts`): cookies, CSRF sob demanda e renovado após login/logout, uma nova tentativa em 403 de CSRF, `ApiError` tipado, `Retry-After`, aviso central de 401 | ✅ |
| F02 React Router, sessão real (`app/SessionProvider.tsx`), login, rotas protegidas, 404, retorno seguro após login | ✅ |
| F03 criar conta e confirmar e-mail (token do fragmento removido da barra antes de qualquer requisição; consumo único) | ✅ |
| F04 recuperar senha (3 etapas, dados só em memória) | ✅ |
| F05 minha conta (nome real, senha, troca de e-mail + página de confirmação); aba "Dispositivos" removida (a API não tem) | ✅ |
| F06/F07 laboratórios, membros e papéis reais; dados locais antigos de laboratório apagados (decisão do dono); lâminas de laboratórios antigos aparecem como avulsas | ✅ |
| F08 convites de participação (gestão + `/convites/aceitar`) | ✅ |
| F09 orientação (seção no laboratório + `/orientacoes/convites/aceitar`) | ✅ |
| F10 notificações reais + SSE com reconexão | ✅ |
| F12 site no Docker Compose (Nginx, healthchecks, IP fixo do web como proxy confiável) | ✅ |
| **F11 painel do admin da plataforma** | ⏳ back pronto no PR #46 (aberto); **tela não feita** — ver seção 5 |

---

## 4. O que ainda é local no navegador

- **Lâminas e contagens** (`apps/web/src/storage.ts`, `domain.ts`, `Slides.tsx`; chave `localStorage` `citogenetica:workspace:v1`). Funcionam (criar lâmina, contar, resumo, IM, backup/importação em JSON), mas **não vão para o servidor**: não aparecem em outro aparelho, o orientador e o admin não veem, e somem se o navegador for limpo. **É a próxima grande etapa (Etapa 2).**
- **Foto de perfil e modo escuro** (`preferences.ts`, chave `citogenetica:preferences:v1`) — decisão: podem continuar locais.
- Não há mais nada inventado ("mock") na tela: laboratórios, convites e usuário de exemplo foram removidos.

**Regra do `apps/web/AGENTS.md` que vale para a Etapa 2:** nunca perder dados do usuário. Ao migrar as lâminas locais para a API, faça backup e importação explícita (a pessoa escolhe), sem apagar o local até confirmar.

---

## 5. Trabalho em aberto agora

1. **PR #46 — `feat/admin-listagem-laboratorios` (aberto, base `dev`).** `GET /api/v1/admin/laboratorios` só para papel global ADMIN: id, nome, estado, quantidade de membros ativos, administradores ativos (nome + e-mail). Paginação no padrão do projeto (máx. 100), busca por nome sem acento/caixa, filtro por estado, ordenação estável, carga em lote (sem N+1). 205 testes verdes. **Falta o dono mandar mesclar.**
2. **F11 front — tela do admin da plataforma: NÃO começou.** Ver tarefa T1 abaixo.
3. **PRs do Dependabot abertos** (#3, #4, #5, #6, #7, #9, #10, #11, #18 e possivelmente outros). Alguns são saltos MAJOR (Spring Boot 4, springdoc 3, JRE 25, Maven com Temurin 27). **O projeto mira Java 17 e Boot 3 — não mescle às cegas.** Patches/minors podem ser avaliados um a um (rodar a suíte). Pergunte ao dono antes de qualquer MAJOR.
4. **Branches já mescladas no GitHub** não foram apagadas (o ambiente anterior bloqueou). Podem ser apagadas pelo dono.

---

## 6. Lista de tarefas (ordem recomendada)

Cada tarefa = uma branch = um PR. Marque no `TODO.md` ao concluir (num PR `docs/` ou junto, se for pequeno).

### T1 · F11 front — painel do admin da plataforma *(pequena; depende do PR #46)*
- Branch `feat/web-admin-plataforma` empilhada sobre `feat/admin-listagem-laboratorios` (ou a partir de `dev` depois do #46 mesclado).
- Rota `/admin/laboratorios`, visível e acessível **só** para quem tem papel global `ADMIN` (`role.nome` no `GET /api/v1/usuarios/me` — confira o campo real). Link no menu da conta só para ele. Usuário comum que abrir a URL → página "sem permissão" (o servidor também bloqueia).
- Lista paginada com busca por nome e filtro por estado; colunas: nome, estado, membros ativos, administradores (nome + e-mail, com `mailto:`).
- Botões **Desativar** / **Reativar** (`PATCH /api/v1/admin/laboratorios/{id}/desativar|reativar`) com confirmação acessível e mensagem explicando o efeito (desativado: dados ficam, membros veem, ninguém altera nem convida).
- Tudo via `src/api/` (`admin-api.ts` + contratos). Testes Vitest: visível só para ADMIN, paginação, filtros, desativar/reativar, erros 403/409.
- Para testar ao vivo: crie uma conta, confirme e rode o comando de bootstrap (seção 3.1) com o e-mail dela.

### T2 · Etapa 2 back — protocolos, lâminas e contagens na API *(grande — ver seção 7)*
Dividir em PRs empilhados: (a) protocolos/fases/tipos + seed; (b) lâminas; (c) registros de contagem + cálculos; (d) histórico, edição e desfazer.

### T3 · Etapa 2 front — ligar a tela de contagem à API
- Trocar `storage.ts` por uma camada `src/api/` + repositório, mantendo a UI atual de `Slides.tsx` o mais intacta possível.
- **Migração das lâminas locais:** tela que lista o que existe no navegador e oferece "enviar para o servidor" (por laboratório ou avulsas), com backup baixável antes e sem apagar o local até o servidor confirmar.
- Aplicar visibilidade: o orientador e o admin veem as lâminas do pesquisador (somente leitura); só o dono edita.

### T4 · Etapa 3 — dados
Exportação JSON versionada, importador dos backups dos HTMLs antigos (sem duplicar), CSV (resumo e uma linha por registro; **escapar células que começam com `=`, `+`, `-`, `@`**), PDF. Formatos dependem de resposta do laboratório (seção 12).

### T5 · Etapa 4 — offline/sync
PWA (manifest, service worker, IndexedDB), fila persistente de operações idempotentes (`operacao_id` + hash do payload), revisões, conflitos com tela de resolução, consulta incremental com cursor. **Só lâminas e contagens podem ser criadas offline** (decisão do dono). Guardar a hora do aparelho como dado separado e informativo; a oficial é a do servidor. O contrato de sincronização já está desenhado no ADR 0003.

### T6 · Correções do front listadas no `TODO.md` §2.1
Histórico por dia + edição de registro; "desfazer" seguro após recarregar; lote como 1 registro com `quantidade`; importação mesclando por id; escopo das anormalidades personalizadas; tratar falha ao excluir lâmina; "criada por" real. Várias delas se resolvem naturalmente na T2/T3 — não duplique trabalho.

### T7 · Etapas 5–7
Projetos/experimentos/editor de protocolos; fotos (RustFS privado, upload em duas fases, checksum) e comentários; comparações e gráficos (fórmulas a validar com o laboratório).

### T8 · Antes de produção
Hospedagem, HTTPS, cookie `Secure=true`, credencial própria e limitada da API no RustFS, backup do banco e do storage, imagens por digest, LGPD (termo de uso/aviso de privacidade), `API_SECURITY_TRUSTED_PROXY_REGEX` correto para o proxy real, revisar CSP do Nginx.

---

## 7. Etapa 2 — Contagem na API (detalhamento)

Planejamento original (TODO §1.4): *Protocolo, Versão (imutável quando publicada), Fase (`conta_como_divisao`), Tipo de anormalidade (fixo e personalizado, normalização), Lâmina, Registro (`quantidade`, `registrado_em`, `fuso_registro`, exclusão lógica, versão), cálculos oficiais (totais, %, IM), desfazer, edição, histórico; seed de meiose e mitose.*

### 7.1 Antes de começar
- Leia `apps/web/src/domain.ts` — ali estão as fases de meiose e mitose, os tipos de anormalidade e os cálculos atuais (fonte da verdade do comportamento esperado pelo laboratório). Leia `docs/registro-meiose.html` e `docs/registro-Ciclo_celular.html` (protótipos originais).
- Releia o ADR 0003 (UUID, idempotência, dono, conflito por versão) — as entidades da Etapa 2 **devem** seguir esse contrato porque serão sincronizadas offline na Etapa 4.

### 7.2 Desenho sugerido (confirme e ajuste com bom senso)
- Novo módulo **`contagem`** (pacotes `api` e `internal/{web,application,domain,persistence}`), adicionado ao ArchUnit e a `docs/arquitetura.md`. Depende de `shared` e de `laboratorio.api` (`LaboratorioAccessQuery`) e `usuario.api`; **nunca** de `internal` de outro módulo.
- Entidades novas estendem `shared.persistence.UuidEntity` (UUID, `Instant`/`TIMESTAMPTZ`, `@Version`). IDs criados no cliente validados com `@CanonicalUuidV4` (offline).
- **Protocolo / Versão / Fase / TipoAnormalidade:** seed de meiose e mitose numa migration; versão publicada é imutável; fase com `conta_como_divisao` (para o IM). Tipos personalizados: escopo usuário + laboratório + fase, com nome normalizado (sem duplicar por acento/caixa).
- **Lâmina:** pertence a um pesquisador (participação) dentro de um laboratório (ou avulsa? — **perguntar ao dono** se lâmina avulsa sem laboratório continua existindo no servidor). Campos atuais do front: nome, protocolo, espécie, tratamento, observações, meta (opcional).
- **Registro de contagem:** `quantidade` (lote = 1 registro), fase, status normal/anormal, tipos de anormalidade (**uma célula pode ter mais de uma** — decisão do dono), `registrado_em` (servidor) + hora do aparelho e fuso (informativos), exclusão lógica, versão.
- **Cálculos oficiais no servidor** (totais por fase, %, IM) — os do front passam a ser só exibição/prévia.
- **Autorização:** toda leitura passa por `LaboratorioAccessQuery.podeVerDadosDoPesquisador`; toda escrita por `podeEditarDadosDoPesquisador`. Admin do laboratório **não** herda permissão de editar contagem. Laboratório desativado: só leitura. Laboratório alheio → 404; sem papel → 403; conflito de versão → 409.
- **Idempotência** desde já (prepara a Etapa 4): criação aceita `id` do cliente e é idempotente; atualização exige `versaoEsperada`.
- Testes de integração com Testcontainers cobrindo a matriz de acesso (dono, orientador ativo, coorientador, orientador encerrado, admin, membro comum, ex-membro, alheio, laboratório desativado), cálculos com casos conhecidos dos protótipos, idempotência e concorrência.

### 7.3 Perguntas de produto que travam detalhes (pergunte ao dono antes de decidir)
- Limites do lote, meta configurável e casas decimais dos percentuais.
- Alcance do "desfazer" (só no aparelho? entre aparelhos?).
- Lâmina avulsa (sem laboratório) existe no servidor?
- Quais eventos da contagem geram notificação (se algum).

---

## 8. Arquitetura e contratos que você precisa respeitar

### 8.1 Back
- Cada módulo: `api` (contratos públicos) e `internal` (`web`, `application`, `domain`, `persistence`). **Outro módulo nunca importa `internal`.** `shared` não depende de módulos de negócio. Teste: `apps/api/src/test/java/br/ufla/citogenetica/architecture/ModuleArchitectureTest.java`.
- Integração entre módulos: consulta pública mínima (ex.: `usuario.api.UsuarioRosterQuery`, `laboratorio.api.LaboratorioAccessQuery`) ou evento público (`<modulo>.api.event`) consumido após o commit.
- Erros: `ErrorResponseDTO` (`status`, `error`, `message`, `path`, `details`), mensagens em pt-BR. `BadRequestException` (400), `ConflictException` (409), `NotFoundException` (404), `BusinessException` (422), `TooManyRequestsException` (429 + `Retry-After`).
- Lock do laboratório **sempre antes** das linhas filhas (evita deadlock); mesma ordem em todos os fluxos.
- Links de e-mail: token no **fragmento** (`#token=`), nunca na query; guardado só como hash; URLs do front configuráveis e validadas (HTTPS fora de localhost).
- Respostas genéricas de propósito (cadastro, reenvio, recuperação, troca de e-mail, links inválidos) — **não "conserte"**.
- OpenAPI completo em toda rota nova e path registrado em `CitogeneticaApplicationTests`.

### 8.2 Front
- Regras obrigatórias em `apps/web/AGENTS.md` e identidade visual em `DESIGN.md`.
- **Componentes nunca chamam `fetch`, `localStorage` nem `EventSource` direto** — tudo por `src/api/` (há um teste que varre `src/` e falha se houver `fetch(` fora de `src/api/`).
- Tipos em `src/api/contracts/*` espelham **exatamente** os DTOs Java. Não invente campos: leia o controller/DTO ou `/v3/api-docs`.
- CSRF em toda mutação (o cliente faz sozinho); 401 → sessão cai e vai para `/entrar` com retorno seguro; 403/404/409/429 com mensagens claras.
- Tokens de links (confirmação, troca de e-mail, convites): use `app/confirmation-token.ts` (remove da barra antes de qualquer requisição, consumo único, sobrevive ao StrictMode) e `app/invitation-token-context.tsx` (mantém em memória durante o login).
- Nada de texto "protótipo/demo/mock" visível; não prometa função que não existe; estados vazio/carregando/erro/sucesso; acessibilidade (rótulos, `aria-invalid`, foco); responsivo.
- `src/lib/type-scale.ts` e `src/lib/size-context.tsx` têm trechos gerados — **não editar à mão**.
- Pasta `.impeccable/` é local (ignorada pelo git) — não versionar.

### 8.3 Infra
- Compose: serviços `api`, `web`, `db`, `rustfs`, `mailpit`. O `web` tem IP fixo `10.89.57.10` na sub-rede `10.89.57.0/24`, e a API confia nos cabeçalhos `X-Forwarded-*` **só dele** (`API_SECURITY_TRUSTED_PROXY_REGEX`, padrão no compose). Isso faz os limites por IP verem o cliente real e impede forjar endereço. Se mudar a rede, mantenha essa garantia.
- `API_CORS_ALLOWED_ORIGINS` **precisa** conter o endereço do site (mesmo atrás do proxy): sem isso o login pelo navegador dá `403 Invalid CORS request`.
- Variáveis novas: sempre no `.env.example`; `./scripts/gerar-env.sh --add-missing` acrescenta ao `.env` existente sem sobrescrever.

---

## 9. Como rodar, testar e verificar

```bash
# Tudo no Docker
./scripts/gerar-env.sh                # primeira vez (ou --add-missing quando surgirem variáveis novas)
docker compose up -d --build          # site :3000, API :8080, Mailpit :8025
docker compose down                   # parar (NUNCA use -v sem o dono pedir: apaga o banco)

# Testes da API (precisa de Docker por causa do Testcontainers)
cd apps/api
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 DOCKER_HOST=unix:///var/run/docker.sock \
  ./mvnw -Dmaven.repo.local=/tmp/citogenetica-m2 clean test

# Front
cd apps/web
npm ci
npm test            # node --test + Vitest
npm run build
API_PROXY_TARGET=http://localhost:8080 npm run dev   # Vite com proxy /api
```

**Antes de abrir cada PR:** suíte completa verde (back e/ou front), `git diff --check`, e — sempre que mexer em fluxo de usuário — **teste ao vivo** contra a API com o Mailpit (dá para fazer por `curl` com cookie jar: `GET /api/v1/auth/csrf` → ler `XSRF-TOKEN` do cookie → mandar no header `X-XSRF-TOKEN`; tokens/códigos de e-mail saem da API do Mailpit em `http://localhost:8025/api/v1/search?query=to:<email>` e `/api/v1/message/<ID>`).

---

## 10. Armadilhas conhecidas deste ambiente

- **Java:** o padrão da máquina é o 25; a API exige 17 → use `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` (sem isso: "release version 17 not supported").
- **Docker:** o contexto padrão aponta para o Docker Desktop (parado) → use `DOCKER_HOST=unix:///var/run/docker.sock`.
- **Portas ocupadas nesta máquina** por outros projetos do dono: 5432, 8080, 9000 e 5173. Para testar aqui, use um arquivo de override fora do repositório, por exemplo:
  ```yaml
  services:
    db:     { ports: !override [] }
    rustfs: { ports: !override [] }
    api:    { ports: !override ["127.0.0.1:18080:8080"] }
  ```
  e `docker compose -p citogenetica -f docker-compose.yml -f <override> up -d --build`. **Não toque nos containers de outros projetos** (ex.: `athena-*`).
- Mudou a rede do compose (sub-rede fixa)? Containers antigos exigem `docker compose down` (sem `-v`) uma vez antes do `up`.
- Mudou o `.env`? Recrie o container da API (`up -d api`), senão ele continua com os valores antigos (já causou o 403 de CORS).
- O cache do Maven compartilhado dá erro de lock em sandbox → sempre `-Dmaven.repo.local=/tmp/citogenetica-m2`.
- Arquivo `apps/web/public/images/celulas-editorial.png` pode aparecer como não rastreado: **não adicione** sem o dono pedir.

### Contas de teste no banco local do dono (não existem em banco novo)
| E-mail | Senha | Situação |
|---|---|---|
| `novo-6970@exemplo.org` | `Outra#Senha2026` | Admin do "Lab Teste" |
| `pesq-16695@exemplo.org` | `Pesq#Senha2026` | Membro + Orientadora do "Lab Teste"; dona do "Lab da Pesquisadora" |

Não há conta padrão no projeto (de propósito). Em banco novo: crie pelo site e confirme pelo Mailpit.

---

## 11. Checklist de revisão (erros que já aconteceram)

Revise todo diff contra esta lista — implementadores já erraram em cada item:

- [ ] **Vazamento por tempo/resposta:** respostas "genéricas" precisam ser iguais em status, corpo **e** trabalho feito (ex.: calcular BCrypt também para e-mail inexistente).
- [ ] **Estado revelado pela ordem das checagens:** valide token e destinatário **antes** de checar o estado do laboratório, senão a mensagem revela que ele foi desativado.
- [ ] **Contrato consistente:** link inválido = 400 genérico; senha atual errada em rota logada = 409 (nunca 401 — o front entenderia como sessão expirada).
- [ ] **BCrypt > 72 bytes** com guarda antes de `matches`/`encode`.
- [ ] `IllegalArgumentException`/`IllegalStateException` por entrada do usuário **não** pode virar 500.
- [ ] Mensagens de erro verdadeiras (não mandar "use AAAA-MM-DD" para um campo `Instant`).
- [ ] Comentários e mensagens em pt-BR (já vieram em inglês).
- [ ] Nada fora do escopo (já adicionaram rota DELETE de usuário sem pedido).
- [ ] Não editar arquivo gerado/vendor (`mvnw`, `.mvn/`, trechos gerados de `type-scale.ts`).
- [ ] Edição por script: conte as ocorrências antes de substituir (`str.replace` global já quebrou teste).
- [ ] Configuração do Vite **não** pode ler o `.env` da raiz para o navegador (tem segredos); variáveis do servidor de dev sem prefixo `VITE_`.
- [ ] Laboratório desativado: nenhuma alteração (inclusive encerrar orientação).
- [ ] Rodar a suíte completa você mesmo, ler linha a linha o que mexe em segurança/autorização, conferir OpenAPI e ArchUnit.
- [ ] Teste ao vivo com a API e o Mailpit sempre que houver fluxo de usuário.

---

## 12. Decisões de produto e perguntas em aberto

Fonte oficial: `docs/questoes-validacao.md`. Resumo:

**Decidido**
- Cadastro aberto (qualquer e-mail), sem aprovação manual; senha 8–64, comuns bloqueadas; campos: nome, e-mail, senha.
- Admin da plataforma separado dos papéis do laboratório; **não vê dados de pesquisa**. Na listagem global vê: nome, estado, nº de membros ativos e administradores (nome + e-mail).
- Qualquer usuário confirmado cria laboratório e vira admin dele. Vários admins por laboratório, nunca zero.
- Laboratório tem **só nome** (sem instituição/descrição — tudo é da UFLA).
- Papéis combináveis: MEMBRO, ADMIN, ORIENTADOR. Só admins concedem ADMIN/ORIENTADOR.
- Laboratório desativado: sem alterações nem convites; dados ficam; membros continuam vendo.
- Convite vale 7 dias; reenviar invalida o anterior. Convite direto ao laboratório: admins. Orientador convida só para orientação (e isso também coloca a pessoa no laboratório).
- Coorientação permitida. Vínculo encerrado pelo orientador ou admin (não pelo pesquisador).
- Quem sai/é removido perde acesso; volta só com convite novo. **Admins continuam vendo os dados de quem saiu.**
- Lâminas/contagens: visíveis ao dono, aos orientadores ativos dele e aos admins do laboratório; só o dono edita.
- Uma célula pode ter mais de uma anormalidade.
- Offline: só lâminas e contagens. Hora do aparelho guardada à parte, só informativa.
- Dados de laboratório que existiam só no navegador foram descartados (decisão do dono). **Lâminas locais NÃO** — precisam de migração cuidadosa na Etapa 2.
- Foto de perfil e modo escuro continuam locais.
- Dependências autorizadas no front: React Router, Vitest + Testing Library + jsdom.

**Em aberto (perguntar ao dono / ao laboratório)**
- Limites do lote, meta configurável, casas decimais (Etapa 2).
- Alcance do "desfazer" entre aparelhos (Etapa 2/4).
- Lâmina avulsa (sem laboratório) existe no servidor? (Etapa 2)
- Quais outros eventos geram notificação.
- Backups reais dos HTMLs antigos para testar a importação; formato de CSV/PDF (Excel, R, SISVAR) (Etapa 3).
- Hospedagem e responsável; LGPD (antes da produção).
- O que fazer com os PRs MAJOR do Dependabot.

---

## 13. Mapa de arquivos

```text
apps/api/src/main/java/br/ufla/citogenetica/
  config/          segurança (SecurityConfiguration, CSRF, CORS, proxy confiável), OpenAPI, GlobalExceptionHandler, bootstrap do admin
  shared/          UuidEntity, UuidFactory, @CanonicalUuidV4, paginação (PageResponseDTO), filtros (CriteriaCreator), erros, EmailSender
  infra/           email (SMTP), storage (RustFS/S3)
  usuario/         api/ (UsuarioPrincipal, RosterQuery, ConviteQuery...) + internal/ (auth, cadastro, recuperação, conta, troca de e-mail)
  notificacao/     notificações + SSE; listeners de eventos de convite
  laboratorio/     api/ (LaboratorioAccessQuery, eventos) + internal/ (laboratório, participação, papéis, convites, orientação, admin)
  exemplo/         CRUD de referência do padrão (só ADMIN)
apps/api/src/main/resources/db/migration/   V1..V11 (próxima: V12)
apps/api/src/test/java/.../architecture/ModuleArchitectureTest.java   regras do monólito modular

apps/web/src/
  main.tsx                 só monta o app (e limpa a chave antiga de laboratórios)
  app/                     App (rotas), SessionProvider, páginas de login/cadastro/confirmação/recuperação/conta/aceite de convites
  api/                     http-client, errors, *-api.ts, contracts/ (espelho dos DTOs), cliente SSE
  Laboratories.tsx         área logada: laboratórios, membros, papéis
  LaboratoryInvitations.tsx, LaboratoryOrientations.tsx, NotificationCenter.tsx, RoleFields.tsx
  Slides.tsx + domain.ts + storage.ts   lâminas e contagens (AINDA LOCAIS — Etapa 2)
  preferences.ts           foto e tema (locais)
  storage/legacy-laboratories.ts       limpeza da chave antiga
apps/web/tests/            *.test.mjs (node --test) e *.test.tsx/ts (Vitest)
apps/web/Dockerfile, apps/web/nginx.conf
docker-compose.yml, .env.example, scripts/gerar-env.sh
docs/arquitetura.md, docs/adr/0001..0003, docs/questoes-validacao.md, docs/handoff-coordenacao.md, TODO.md
```

---

**Primeiros passos sugeridos para você, Codex:**
1. Ler este arquivo, `TODO.md`, `docs/arquitetura.md`, `docs/adr/*`, `docs/questoes-validacao.md`, `apps/web/AGENTS.md`, `DESIGN.md`.
2. `git fetch --all && gh pr list` para ver o que mudou desde 2026-11-03.
3. Rodar as duas suítes e confirmar que estão verdes.
4. Pegar a **T1** (tela do admin da plataforma) — é pequena e fecha a Etapa 1.
5. Antes da **T2**, levar ao dono as perguntas da seção 7.3, em linguagem simples.
