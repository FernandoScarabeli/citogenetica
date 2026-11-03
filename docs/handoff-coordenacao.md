# Passagem de coordenação — leia isto antes de qualquer coisa

> Documento para o próximo assistente (Claude) que vai **coordenar** o desenvolvimento deste repositório usando o Codex CLI como implementador. Escrito em 2026-11-03. Se algo aqui divergir do código, o código vence — confira antes de afirmar.

---

## 0. Primeiros 15 minutos (checklist)

1. Leia este arquivo inteiro. Depois: `TODO.md`, `docs/arquitetura.md`, `docs/adr/*.md`, `docs/questoes-validacao.md` (decisões de produto), `apps/api/README.md`, `apps/web/AGENTS.md`, `DESIGN.md`.
2. `git fetch --all` e veja quais PRs já foram mesclados: `gh pr list --state all --limit 30`. A pilha aberta está na seção 4.
3. **Salve na sua memória persistente** as regras da seção 2 (você não tem a memória do PC anterior). São regras do usuário, não sugestões.
4. Confirme que os testes passam: `cd apps/api && ./mvnw -Dmaven.repo.local=/tmp/citogenetica-m2 clean test` (precisa de Docker por causa do Testcontainers).
5. Confirme o Codex: `~/.local/bin/codex --version` (ou `which codex`). A sessão antiga do Codex ficou no outro PC — você vai abrir uma sessão nova com o prompt de contexto da seção 6.4.
6. Pergunte ao usuário só o que for decisão dele. O resto, decida você (seção 2.3).

---

## 1. O projeto em um minuto

- **O que é:** sistema web para registrar e resumir contagens de células em lâminas de citogenética (meiose e mitose, Índice Mitótico) do Laboratório de Citogenética da UFLA. Substitui protótipos HTML locais (`docs/registro-*.html`).
- **Repositório público:** `github.com/FernandoScarabeli/citogenetica`, licença AGPL-3.0-or-later.
- **Monorepo:**
  - `apps/api` — Java 17, Spring Boot 3.3.4, Maven, PostgreSQL 16 + Flyway, Spring Session JDBC (cookie HttpOnly + CSRF), RustFS (S3, AWS SDK v2, bucket privado + URL assinada), Mailpit em dev. Monólito modular verificado por ArchUnit.
  - `apps/web` — React 19 + TypeScript + Vite + Tailwind v4 + lucide-react. **Ainda não fala com a API**: guarda tudo em `localStorage`. Não está no Docker Compose.
- **Rodar:** `./scripts/gerar-env.sh && docker compose up -d --build` na raiz (use `--add-missing` quando surgirem variáveis novas no `.env.example`). Front: `cd apps/web && npm install && npm run dev`.
- **Serviços locais:** API `:8080`, Swagger `/swagger-ui.html`, contrato `/v3/api-docs`, health `/actuator/health`, Mailpit `:8025`, console RustFS `:9090`.

---

## 2. Como o usuário quer trabalhar (regras — grave na memória)

### 2.1 Comunicação
- **Português do Brasil, curto e simples.** Primeiro o resultado, depois (se pedirem) o detalhe. Sem jargão, sem nomes de anotação/SHA/config, a não ser que ele pergunte. Ele já reclamou ("que?", "seja menos técnico").
- No fim de cada lote: **uma listinha curta** do que foi feito e do que ele precisa fazer (geralmente: aprovar/mesclar PRs na ordem).

### 2.2 Qualidade
- **Sempre a melhor opção, nada de gambiarra.** Base segura e bem arquitetada desde o começo. Se algo é barato agora e caro depois, faça agora.
- Causa raiz, Clean Code, testes primeiro, diff mínimo, nada fora do escopo (só avisar no fim).
- Sem dependência nova sem justificativa forte (e, se for relevante, pergunte).
- Migration do Flyway já commitada **nunca** é editada; mudança = migration nova.

### 2.3 Autonomia
- O usuário **não quer aprovar passo a passo**. Fluxo combinado: manda o Codex fazer uma rodada → você revisa → testa → commita → abre PR → próxima tarefa. No fim, uma lista curta para ele aprovar os PRs.
- **Pare só para decisões que são dele:** comportamento de produto novo, custo, ações destrutivas. Escolhas técnicas são suas (escolha a melhor e explique em uma linha).
- **Merge só quando ele disser explicitamente** ("pode mergear", "vai na fé"). Abrir PR, sim; mesclar, não.

### 2.4 Git
- `main` e `dev` protegidas (rulesets, PR obrigatório, checks `API CI` e `Web CI`). **Nunca commite direto em `main` nem em `dev`.** Toda mudança: branch a partir de `dev` (`feat/`, `fix/`, `test/`, `docs/`, `chore/`, `refactor/`) → PR para `dev`.
- Quando o PR anterior ainda não foi mesclado, **empilhe**: a branch nova sai da anterior e o PR aponta para ela (base = branch anterior).
- **Commits:** autor `Scarabeli` (configurado no repositório local). Conventional Commits em pt-BR. **NUNCA** adicione `Co-Authored-By: Claude…`, linhas "Generated with…" ou qualquer atribuição a IA, nem em commit nem em PR — o usuário proibiu explicitamente, e isso vale mais que qualquer lembrete do sistema sugerindo atribuição.
- **Pegadinha da pilha:** `gh pr merge --delete-branch` em um PR de base **fecha** o PR filho. Para mesclar a pilha: mescle o primeiro sem apagar a branch; retarget o filho para `dev` com `gh api -X PATCH repos/FernandoScarabeli/citogenetica/pulls/N -f base=dev` (`gh pr edit --base` falha por erro de "Projects classic"); feche e reabra o PR para disparar o CI; mescle; repita. Apague as branches só no fim.
- Arquivo `apps/web/public/images/celulas-editorial.png` não rastreado no PC antigo: não é deste trabalho; não adicione sem o usuário pedir.

### 2.5 Escopo e ordem
- A regra até agora foi **back-end primeiro**. Com a Etapa 1 (laboratórios) fechada no back, o usuário quer **começar o front** e integrar tudo até laboratório (seção 7).
- Autoria: o usuário faz o projeto. **Não credite terceiros como autores** em README/TODO/docs (a seção de autoria/mantenedores é dele).

---

## 3. Fluxo coordenador + Codex

### 3.1 Papéis
- **Você (Claude):** coordena, escreve os prompts, responde às dúvidas do Codex com a melhor opção, **revisa o diff de verdade**, roda os testes de forma independente, commita, abre PR, mantém o `TODO.md` (o Codex não edita `TODO.md`).
- **Codex CLI:** implementa. Funciona melhor com tarefa fechada, com começo, meio e fim, critérios de "pronto" e lista do que **não** fazer.

### 3.2 Duas fases
- **Fase 1 (somente leitura):** entendimento, diagnóstico com arquivos concretos, 2–3 opções com prós/contras/riscos, recomendação, plano + testes, "RESUMO BURRINHO" (bem simples) e perguntas em aberto. PARA.
- **Fase 2 (escrita):** testes primeiro, diff mínimo, verificação real, autorrevisão, entrega por arquivo + resultados + riscos + sugestões fora do escopo. **Sem stage/commit** — quem commita é você.
- Para tarefas já bem especificadas (decisão tomada), pode ir direto para a fase 2.

### 3.3 Comandos (rode em background e espere a notificação; guarde prompt e saída em arquivo)
```bash
# Fase 1 (somente leitura; rede liberada para pesquisar documentação)
codex exec -m gpt-6-luna -c model_reasoning_effort=xhigh -s read-only \
  -c sandbox_read_only.network_access=true --skip-git-repo-check \
  -C <repo> -o <saida.md> - < <prompt.md> > <log.txt> 2>&1

# Continuar a MESMA sessão (fase 2 ou próxima tarefa) — o id aparece no cabeçalho do log
codex exec resume <session-id> -m gpt-6-luna -c model_reasoning_effort=xhigh \
  -c sandbox_mode='"workspace-write"' -c sandbox_workspace_write.network_access=true \
  --skip-git-repo-check -o <saida.md> - < <prompt.md> > <log.txt> 2>&1
```
- Reaproveitar a sessão mantém o contexto do projeto (economiza explicação). Abra uma sessão nova quando o contexto ficar enorme ou mudar de assunto (ex.: back → front).
- O sandbox do Codex pode não conseguir escrever em `.git`; por isso ele não commita.
- Testes da API: sempre `-Dmaven.repo.local=/tmp/citogenetica-m2` (o cache compartilhado dá erro de lock no sandbox).

### 3.4 Estrutura de prompt que funciona
```
# PAPEL       engenheiro sênior (Java 17/Spring Boot, ou React/TS no front), Clean Code, testes, segurança; revisor exigente; responde em pt-BR
# CONTEXTO    resumo do projeto + o que ler primeiro + "sempre a melhor opção, sem gambiarra"
# ESCOPO      o que fazer, decisões já tomadas (produto e técnicas), rotas/contratos
# TESTES      cenários obrigatórios (inclusive corrida/concorrência e segurança)
# REGRAS      causa raiz; nada fora do escopo (só avisar); sem dependência nova; não editar TODO.md;
              não commitar; nunca imprimir segredos; não mexer em containers alheios / nunca `down -v`
              no que não criou; migrations antigas intactas; pt-BR em comentários e mensagens
# ENTREGA     comando de verificação que precisa ficar verde + formato do relatório
```

### 3.5 O que revisar (o Codex errou nisso nesta fila — confira sempre)
- **Vazamento por tempo/resposta:** no cadastro, o hash BCrypt só era calculado para e-mail novo → dava para descobrir quem tem conta pelo tempo. Respostas "genéricas" precisam ser iguais em status, corpo **e** trabalho feito.
- **Contrato inconsistente:** senha atual errada em rota logada devolvia 401 (o front interpretaria como sessão expirada); o padrão do projeto é 409 "A senha atual está incorreta.".
- **BCrypt > 72 bytes** precisa de guarda antes de `matches`/`encode` (senão erro interno).
- **Substituição global em scripts** (`str.replace` sem contar ocorrências) já quebrou outro teste — ao editar por script, confira quantas vezes o trecho aparece.
- Mensagens de erro que mentem (filtro de `Instant` mandando usar "AAAA-MM-DD").
- Histórico: Codex já editou arquivo gerado/vendor (`mvnw`), manteve API depreciada, consertou banco local à mão em vez de recriar, deixou `IllegalArgumentException` virar 500, escreveu comentários em inglês, adicionou rota fora do escopo (DELETE de usuário).
- Sempre: rodar a suíte completa você mesmo, ler o diff de segurança/autorização linha a linha, conferir OpenAPI e ArchUnit.

---

## 4. Estado em 2026-11-03

### 4.1 Já mesclado em `dev`
PRs #1–#23: isolamento de testes (Testcontainers), CI, correções de segurança da autenticação (sessão JDBC + CSRF no lugar de JWT, recuperação por código de 6 dígitos com HMAC e tentativas limitadas, rate limit em Postgres com 429 + Retry-After), notificações por dono + SSE por usuário, filtros/paginação, monólito modular (`config`, `shared`, `infra`, `usuario`, `notificacao`, `exemplo`) com ArchUnit.

### 4.2 Abertos, aguardando o usuário (mesclar NESTA ordem)
| PR | Branch | Base | Conteúdo |
|---|---|---|---|
| #24 | `docs/questoes-validacao` | `dev` | `docs/questoes-validacao.md` com todas as decisões de produto (independente) |
| #25 | `feat/auto-cadastro-confirmacao` | `dev` | Cadastro público + confirmação de e-mail, política de senha, V6 |
| #26 | `feat/bootstrap-admin` | #25 | Comando de uso único do primeiro admin da plataforma, V7, ADR 0002 |
| #27 | `feat/troca-email-confirmacao` | #26 | Troca de e-mail com confirmação no endereço novo, V8 |
| #28 | `feat/base-uuid` | #27 | `UuidEntity`, `UuidFactory`, `@CanonicalUuidV4`, filtro `Instant`, ADR 0003 |
| #29 | `feat/modulo-laboratorio` | #28 | Laboratório, participação, papéis, regra do último admin, V9 |
| (sem PR) | `feat/convites-laboratorio` | #29 | Convites de participação, V10 — **enviado sem revisão**, ver seção 4.3 |
| #30 | `docs/handoff-coordenacao` | `dev` | Este documento (independente) |

Verifique com `gh pr list` — os números dos dois últimos estarão lá se já tiverem sido abertos.

### 4.3 Em andamento / falta no back da Etapa 1
- **Convites de participação (PR B, V10): SUA PRIMEIRA TAREFA.** O Codex implementou no PC antigo e o código foi enviado **sem revisão** para a branch `feat/convites-laboratorio` (empilhada sobre `feat/modulo-laboratorio`), num commit marcado como "não revisado". **Não há PR ainda.** Faça:
  1. `git fetch && git switch feat/convites-laboratorio`.
  2. Revise o diff contra `feat/modulo-laboratorio` com o checklist da seção 3.5 e o escopo do prompt 6.2 (token só como hash; nada de token em log/evento; aceite exige e-mail confirmado igual ao destino; lock do laboratório antes das linhas filhas; laboratório desativado bloqueia; respostas genéricas; limites; `notificacao` sem importar `laboratorio.internal`).
  3. Rode a suíte completa. Corrija o que achar (você mesmo, se for pequeno; ou uma sessão do Codex com contexto 6.4 + a correção).
  4. Commit das correções, push, e abra o PR com base `feat/modulo-laboratorio` (corpo em pt-BR simples, sem atribuição a IA). O relatório do Codex sobre esta entrega está em `docs/handoff-convites-relatorio.md` nesta mesma branch (apague esse arquivo no seu commit de correção — não deve ir para o PR).
  5. Se a branch não existir, refaça a partir do prompt da seção 6.2.
- **Orientação (PR C, V11):** **não começou.** Especificação completa na seção 6.3.

---

## 5. O back-end hoje (resumo técnico)

### 5.1 Módulos e regras de arquitetura
- Raízes: `config` (composição Spring, segurança, web), `shared` (persistência base, paginação, filtros, validação, erros, contrato de e-mail), `infra` (`email` SMTP, `storage` RustFS/S3), módulos de negócio `usuario`, `notificacao`, `laboratorio`, `exemplo` (CRUD de referência de padrão, só ADMIN).
- Cada módulo: `api` (contratos públicos para outros módulos) e `internal` (`web`, `application`, `domain`, `persistence`). **Outro módulo nunca importa `internal`.** `shared` não depende de módulos de negócio. ArchUnit em `apps/api/src/test/java/.../architecture/ModuleArchitectureTest.java` — ao criar módulo, adicione a raiz lá e em `docs/arquitetura.md`.
- Integração entre módulos: API pública mínima (ex.: `usuario.api.UsuarioRosterQuery`) ou eventos públicos (`<modulo>.api.event`) consumidos após commit.

### 5.2 Segurança (como o front deve conversar com a API)
- **Sessão por cookie** `CITOGENETICA_SESSION` (HttpOnly), Spring Session JDBC. Sem JWT.
- **CSRF:** obter token em `GET /api/v1/auth/csrf`; enviar no header `X-XSRF-TOKEN` em **todo** POST/PUT/PATCH/DELETE, inclusive login, cadastro e confirmações. Requisições com `credentials: 'include'`.
- CORS: origens em `API_CORS_ALLOWED_ORIGINS`.
- **Respostas genéricas de propósito** (não "consertar" no front): cadastro, reenvio de confirmação, recuperação de senha e troca de e-mail respondem **202** igual para qualquer e-mail. Login de conta pendente = 401 genérico.
- **429** com `Retry-After` quando o limite estoura — o front deve mostrar "tente novamente em X".
- **Links de e-mail** trazem o token no **fragmento** (`<URL do front>#token=<token>`), nunca na query. A página do front lê o fragmento, limpa o endereço (`history.replaceState`) e faz `POST` com `{ "token": ... }`. URLs configuradas no `.env`: `API_FRONTEND_EMAIL_CONFIRMATION_URL`, `API_FRONTEND_EMAIL_CHANGE_CONFIRMATION_URL`, `API_FRONTEND_LABORATORY_INVITATION_URL` (HTTPS obrigatório fora de localhost).
- Papéis globais: `ADMIN` (plataforma) e `USER`. Papéis do laboratório são **outros** (MEMBRO/ADMIN/ORIENTADOR) e o admin da plataforma **não** ganha acesso aos dados dos laboratórios.
- Laboratório alheio ou inexistente → **404** (não revela existência). Membro sem o papel → **403**. Conflito de estado/versão → **409**.
- Erros vêm como `ErrorResponseDTO` (`status`, `error`, `message`, `path`). Mensagens já estão em pt-BR e podem ser exibidas.

### 5.3 Rotas
**Auth (`/api/v1/auth`)** — `GET /csrf`; `POST /login`, `/logout`; `POST /register` (202, corpo `{nome, email, senha}`, papel USER pelo servidor); `POST /reenviar-confirmacao` (202); `POST /confirmar-email` (204, `{token}`; não cria sessão); `POST /confirmar-troca-email` (204, `{token}`; encerra todas as sessões); `POST /recuperar-senha` (202), `/validar-codigo`, `/redefinir-senha` (código de 6 dígitos).
**Usuário (`/api/v1/usuarios`)** — `GET/PUT /me` (nome); `PUT /me/senha` (senha atual + nova; 409 se atual errada); `POST /me/email` (`{novoEmail, senhaAtual}`, 202; 409 se senha errada); admin: `GET /`, `GET /{id}`, `PUT /{id}` (nome), `PATCH /{id}` (status).
**Notificações** — `GET /api/v1/notificacoes` (paginado, filtros), `GET /nao-lidas/count`, `PATCH /{id}/lida`, `PATCH /marcar-todas-lidas`; SSE em `GET /api/events/subscribe` (autenticado por cookie).
**Laboratórios (`/api/v1/laboratorios`)** — `POST /` (201, `{nome}`; criador vira MEMBRO+ADMIN); `GET /` (só os meus); `GET /{id}`; `PATCH /{id}` (nome, admin local); `GET /{id}/participacoes` (todos os membros ativos veem nome, e-mail, papéis); `PATCH /{id}/participacoes/{pid}/papeis` (`{versaoEsperada, papeis:[...]}`, 409 se versão velha ou se deixar sem admin); `DELETE /{id}/participacoes/me` (sair; 409 se último admin); `DELETE /{id}/participacoes/{pid}` (remover, admin local).
**Admin da plataforma** — `PATCH /api/v1/admin/laboratorios/{id}/desativar` e `/reativar`.
**Convites (PR B)** — conferir no código/Swagger após o merge: envio/listagem/reenvio/revogação sob `/api/v1/laboratorios/{id}/convites`, aceite e recusa em `POST /api/v1/convites/aceitar` e `/recusar` com `{token}` no corpo, exigindo sessão com e-mail confirmado igual ao do convite.
- IDs de laboratório/participação/convite são **UUID v4 canônico minúsculo**; outro formato → 400.

### 5.4 Banco (Flyway)
V1 base · V2 recuperação segura + rate limit · V3 Spring Session · V4 dono da notificação · V5 `unaccent` · V6 confirmação de e-mail · V7 marcador do bootstrap de admin · V8 troca de e-mail · V9 laboratório/participação/papéis + trigger adiada do último admin · V10 convites (PR B). Próxima livre: **V11**.

### 5.5 Primeiro administrador da plataforma
Não há admin semeado. A pessoa se cadastra e confirma o e-mail; quem opera o deploy roda uma vez:
```bash
docker compose run --rm --no-deps api --run-bootstrap-admin --app.bootstrap-admin.email=pessoa@exemplo.org
```
Detalhes e recuperação manual: `apps/api/README.md` e ADR 0002.

### 5.6 Entidades novas
Estendem `shared.persistence.UuidEntity` (UUID atribuído, `Instant`/`TIMESTAMPTZ`, `@Version`). IDs do servidor via `UuidFactory`; IDs vindos do cliente validados com `@CanonicalUuidV4`. Contrato de sincronização (idempotência, dono, conflito por versão) no ADR 0003. Entidades antigas (`Usuario`, `Notificacao`, `Exemplo`, `Role`) continuam `Long`.

---

## 6. Prompts prontos

### 6.1 Observação
Os prompts abaixo assumem uma sessão do Codex que já conhece o projeto. Numa sessão nova, comece com o prompt de contexto (6.4) e depois mande a tarefa.

### 6.2 Convites de participação (só se o PR B não existir)
```
FASE 2 do PR B — convites de participação (V10), branch `feat/convites-laboratorio` empilhada sobre `feat/modulo-laboratorio`. Não faça stage nem commit.
Decisões de produto (finais): convite direto ao laboratório só por admins do laboratório; o admin pode indicar papéis adicionais (ADMIN/ORIENTADOR) além de MEMBRO; validade 7 dias configurável; reenviar invalida o link anterior na hora; admin pode revogar; destinatário por e-mail, pode não ter conta (cadastra, confirma e aceita logado); aceite exige sessão com e-mail confirmado igual ao destino normalizado, senão erro genérico; quem saiu/foi removido volta só por convite novo (nova participação); laboratório desativado não envia, reenvia nem aceita; convite para membro ativo não duplica participação; destinatário pode recusar.
Técnico: tabela própria; token 32 bytes guardado só como SHA-256; link `#token=` com URL de front configurável e validada; `POST /api/v1/convites/aceitar` e `/recusar` com token no corpo; consumo condicional com lock; índice único parcial de convite pendente por (laboratório, e-mail); e-mail após commit; evento público sem token em `laboratorio.api.event` consumido por `notificacao` (nova transação após commit) para avisar quem já tem conta; API pública mínima de `usuario` para normalizar/consultar e-mail confirmado; rate limit de envio por laboratório/autor e de aceite por IP/usuário dentro do módulo; limpeza de vencidos (agendador desligado no perfil de comando). Deixe o fluxo de aceite reaproveitável pelo convite de orientação (PR C).
Testes: envio só por admin (membro 403, alheio 404); sem conta → cadastra, confirma, aceita; e-mail divergente; token inválido/expirado/reutilizado/substituído/revogado; aceite concorrente; dois convites para a mesma pessoa aceitos juntos; reingresso; laboratório desativado; recusa; papéis adicionais; notificação criada; token ausente de banco/log/evento; limites. Suíte completa verde.
```

### 6.3 Orientação (PR C, V11) — próxima tarefa do back
```
FASE 1 curta + FASE 2 do PR C — orientação, branch `feat/orientacao-laboratorio` empilhada sobre `feat/convites-laboratorio`. Não faça stage nem commit.
Decisões de produto (finais, do usuário):
- O orientador (participante ativo com papel ORIENTADOR) convida uma pessoa para ser orientada por ele, por e-mail. Se a pessoa ainda não é membro do laboratório, o MESMO convite também a coloca no laboratório (participação nova, papel MEMBRO) ao aceitar. Admin do laboratório também pode convidar para orientação em nome de um orientador? NÃO decidido — não implemente; só o próprio orientador convida.
- O pesquisador aceita (ou recusa) logado, com e-mail confirmado igual ao destino.
- Um pesquisador pode ter vários orientadores (coorientação). O mesmo par não pode ter dois vínculos ativos. Ninguém orienta a si mesmo.
- Quem encerra um vínculo ativo: o próprio orientador ou um admin do laboratório. O pesquisador NÃO encerra.
- Saída ou remoção do laboratório (de qualquer um dos dois) encerra os vínculos ativos dessa participação. Não permitir retirar o papel ORIENTADOR de alguém com vínculos ativos sem antes encerrá-los (409 claro).
- Convite vale 7 dias, reenviar invalida o anterior, laboratório desativado bloqueia tudo.
- Visibilidade futura de lâminas/contagens: dono + seus orientadores ativos + admins do laboratório. Crie agora a API pública `laboratorio.api.LaboratorioAccessQuery` com consultas de leitura (ex.: podeVerDadosDoPesquisador(labId, pesquisadorUsuarioId, leitorUsuarioId)) e edição (só o dono), testada pela matriz dono/orientador/coorientador/admin/membro/ex-membro/alheio — será usada pelas lâminas na Etapa 2.
Técnico: reaproveite a infraestrutura de token/aceite/e-mail/evento/limites do PR B; tabelas `convite_orientacao` e `vinculo_orientacao` (UUID, `UuidEntity`), índices únicos parciais (vínculo ativo por par; convite pendente por orientador+e-mail), CHECK contra auto-orientação, lock do laboratório antes das linhas filhas (mesma ordem do PR A).
Rotas sugeridas: `POST /api/v1/laboratorios/{id}/orientacoes/convites` (202), `GET /api/v1/laboratorios/{id}/orientacoes` (vínculos que o usuário pode ver: os seus como orientador/pesquisador; admins veem todos), `DELETE /api/v1/laboratorios/{id}/orientacoes/{vinculoId}` (204; orientador do vínculo ou admin), aceite/recusa pelo mesmo endpoint de convites ou endpoint próprio — escolha o mais limpo e justifique.
Testes: fluxo completo com pessoa sem conta e com membro existente; coorientação; par duplicado; auto-orientação; pesquisador tentando encerrar (403); admin encerrando; saída/remoção encerra vínculos; retirar ORIENTADOR com vínculo ativo (409); corrida de aceite; matriz de acesso; laboratório desativado; ArchUnit e OpenAPI. Suíte completa verde.
Entregue: arquivos por área, rotas com exemplos, migration, testes (quantos), riscos.
```
Depois do PR C: atualize `TODO.md` (marcar 1.3/1.4 Etapa 1 e o que ficou pronto) num PR `docs/` e passe para o front (seção 7).

### 6.4 Prompt de contexto para uma sessão nova do Codex
```
# PAPEL
Você é engenheiro sênior (Java 17/Spring Boot 3 e React 19/TypeScript), Clean Code, testes e segurança; revisor exigente. Responda sempre em pt-BR.
# CONTEXTO
Repositório `citogenetica` (UFLA): registro de contagens de células em lâminas. Monorepo: `apps/api` (Spring Boot 3.3.4, Postgres/Flyway, sessão JDBC + CSRF, monólito modular com ArchUnit) e `apps/web` (React 19 + Vite + Tailwind v4, ainda em localStorage). Leia primeiro: `docs/handoff-coordenacao.md` (seções 1, 5 e 7), `docs/arquitetura.md`, `docs/adr/`, `docs/questoes-validacao.md`, `apps/web/AGENTS.md`, `DESIGN.md`, `TODO.md`.
Diretriz do dono: sempre a melhor opção, sem gambiarra, base segura desde o começo.
# REGRAS
Causa raiz; nada fora do escopo (só avise no final); sem dependência nova sem justificar e eu aprovar; pergunte quando houver ambiguidade real; nunca imprima segredos; não edite `TODO.md`; não faça stage nem commit; migrations antigas intactas; não mexa em containers que você não criou e nunca `docker compose down -v`; comentários, mensagens e docs em pt-BR.
Testes da API: `cd apps/api && ./mvnw -Dmaven.repo.local=/tmp/citogenetica-m2 clean test`. Front: `cd apps/web && npm test && npm run build`.
Agora só confirme que leu e resuma em 10 linhas o que entendeu. Não altere nada.
```

---

## 7. Próxima grande fase: front-end integrado até laboratório

### 7.1 Situação do front
- `apps/web/src`: `main.tsx` (app e roteamento simples), `Landing.tsx`, `Laboratories.tsx` + `laboratories.ts` (laboratórios simulados em localStorage), `Notifications.tsx`, `domain.ts` (contagem/lâminas), `storage.ts`, `preferences.ts`, `lib/type-scale.ts` e `lib/size-context.tsx` (tipografia — **não editar à mão os trechos gerados**), CSS próprios + Tailwind v4. Testes em `apps/web/tests/*.test.mjs` com `node --test`.
- **Regras do front estão em `apps/web/AGENTS.md` — obrigatórias.** Destaques: nada de "protótipo/demo/mock" visível; não prometer função que não existe; preservar dados do usuário (migração + backup se mudar formato); componentes não acessam `localStorage` nem `fetch` direto — tudo por uma camada de dados com contratos; permissões são garantidas no back (front só para experiência); acessibilidade, responsivo, estados vazio/carregando/erro/sucesso; identidade em `DESIGN.md`.
- O que **não** muda nesta fase: contagem de lâminas continua local (a API da Etapa 2 ainda não existe). Só autenticação, conta, laboratórios, membros, convites, orientação e notificações passam a ser reais.

### 7.2 Como conduzir
- Uma **sessão nova** do Codex para o front (prompt 6.4), depois uma tarefa por vez, na ordem abaixo. Cada tarefa = uma branch (`feat/web-...`) = um PR para `dev` (empilhe se o anterior não foi mesclado).
- Antes de cada tarefa de tela: o Codex deve olhar o contrato real em `/v3/api-docs` (ou nos controllers) — **não inventar campos**.
- Revisão sua em cada PR: sem `fetch` fora da camada `api/`, CSRF em toda mutação, nenhuma mensagem que revele se e-mail existe, tratamento de 401 (sessão caiu → login), 403, 404, 409, 429 (`Retry-After`), token do fragmento removido da barra de endereço, `npm test` e `npm run build` verdes, e teste manual com o back no ar (Mailpit para os e-mails).
- Dependência nova (ex.: roteador, cliente de dados, biblioteca de testes de componente) só com justificativa; pergunte ao usuário se for estrutural.

### 7.3 Lista de tarefas do front (começo, meio e fim)

**F00 · Fase 1 do front (somente leitura).** Codex lê `apps/web`, `AGENTS.md`, `DESIGN.md` e o OpenAPI e propõe: estrutura de pastas (`api/`, `features/`…), roteamento (há roteador? precisa?), estado de sessão, padrão de formulários e erros, como testar componentes (hoje só `node --test`), como rodar front + API juntos em dev (proxy do Vite vs CORS + cookie `SameSite`), e a lista F01–F12 ajustada. Você decide e segue. *Pronto quando:* plano aprovado por você e eventuais dependências novas autorizadas pelo usuário.

**F01 · Base de integração.** Cliente HTTP único em `src/api/`: `credentials: 'include'`, busca e guarda do token CSRF (`GET /api/v1/auth/csrf`), header `X-XSRF-TOKEN` em mutações, renovação do CSRF após login/logout, conversão de `ErrorResponseDTO` em erro tipado, tratamento central de 401/403/404/409/429 (com `Retry-After`). Variável `VITE_API_URL` (ou proxy do Vite em dev). Testes do cliente. *Pronto quando:* chamadas reais funcionam contra a API local e nenhum componente usa `fetch` direto.

**F02 · Sessão e rotas protegidas.** Contexto de sessão com `GET /api/v1/usuarios/me`, login (`POST /auth/login`), logout, redirecionamento para login quando 401, rotas públicas vs logadas. Substituir o usuário fixo (`demoUser` ou equivalente). *Pronto quando:* recarregar a página mantém a sessão; sessão expirada leva ao login sem perder o que der para preservar.

**F03 · Cadastro e confirmação de e-mail.** Tela de cadastro (nome, e-mail, senha; regra de senha: 8–64 caracteres, senhas comuns recusadas pelo servidor — mostrar a mensagem do 400), tela "confira seu e-mail" (sempre a mesma, nunca dizer se o e-mail existe), reenvio (`/auth/reenviar-confirmacao`), página do link (`#token=` → `POST /auth/confirmar-email` → limpa a URL → "e-mail confirmado, faça login"). Estados de link inválido/expirado com opção de reenviar. *Pronto quando:* fluxo completo funciona com o Mailpit.

**F04 · Recuperação de senha.** Pedir código (`/recuperar-senha`, resposta sempre igual), validar código de 6 dígitos (`/validar-codigo`), definir nova senha (`/redefinir-senha`), 429 com contagem regressiva. *Pronto quando:* fluxo completo com o Mailpit; mensagens não revelam existência do e-mail.

**F05 · Minha conta.** Ver/editar nome (`PUT /usuarios/me`), trocar senha (`PUT /usuarios/me/senha`, 409 = senha atual errada), trocar e-mail (`POST /usuarios/me/email` → "enviamos um link para o novo endereço; o atual continua valendo até confirmar"), página do link de troca (`#token=` → `POST /auth/confirmar-troca-email` → avisar que todas as sessões foram encerradas e levar ao login). *Pronto quando:* os três fluxos funcionam e a troca de e-mail derruba a sessão como esperado.

**F06 · Laboratórios reais.** Trocar `laboratories.ts` (localStorage) pela API: listar os meus, criar (vira admin), ver, renomear (só admin; 403 vira mensagem clara). Migrar/descartar os laboratórios simulados com cuidado (regra de preservar dados do `AGENTS.md`: se havia dados locais de laboratório, decida com o usuário o que fazer — não apague em silêncio). *Pronto quando:* nada de laboratório vem mais do localStorage.

**F07 · Membros e papéis.** Lista de membros (nome, e-mail, papéis — todos os membros veem), alterar papéis (só admin; enviar `versaoEsperada`; 409 → recarregar e avisar), sair do laboratório (409 se último admin → explicar), remover membro (admin). *Pronto quando:* todas as regras do back aparecem como mensagens claras, sem botões que não funcionam para quem não tem o papel.

**F08 · Convites de participação.** Admin convida por e-mail (com papéis adicionais opcionais), lista pendentes, reenvia, revoga. Página do link de convite (`#token=`): se não logado → login ou cadastro e volta ao convite depois; se logado com e-mail diferente → mensagem genérica; aceitar/recusar. *Pronto quando:* convidar alguém sem conta, essa pessoa se cadastrar, confirmar e entrar no laboratório funciona de ponta a ponta.

**F09 · Orientação.** (Depende do PR C.) Orientador convida para orientação; pesquisador aceita/recusa pelo link; lista de vínculos; encerrar vínculo (orientador ou admin; o pesquisador não vê o botão). *Pronto quando:* fluxo completo, inclusive pessoa que ainda não era membro entrando no laboratório pelo convite de orientação.

**F10 · Notificações reais.** Sino com contagem (`/nao-lidas/count`), lista paginada, marcar lida/todas, SSE (`/api/events/subscribe` com cookie) com reconexão e sem duplicar conexões entre abas além do limite. *Pronto quando:* convite recebido aparece no sino em tempo real.

**F11 · Admin da plataforma (mínimo).** Para o papel global ADMIN: desativar/reativar laboratório (por ID ou lista, se existir rota — se não existir rota de listagem global, **não invente**; peça o endpoint como tarefa de back). *Pronto quando:* só aparece para ADMIN global.

**F12 · Front no Docker Compose (TODO T02b).** Serviço `web` no compose (build de produção servido por servidor estático com proxy para a API, ou o que a fase F00 decidir), healthcheck também no serviço `api`, README atualizado. *Pronto quando:* `docker compose up -d --build` sobe tudo e o fluxo F03 funciona pelo endereço do compose.

Ao fim: atualizar `TODO.md` seção 2.2 e o README (status: front já fala com a API).

### 7.4 Pendências que podem aparecer no caminho
- Rota de listagem global de laboratórios para o admin da plataforma não existe.
- Quais eventos geram notificação ainda está em aberto (`docs/questoes-validacao.md`); convites são o primeiro caso.
- Dependabot: há PRs de versão MAJOR (Spring Boot 4, JRE 25, springdoc 3) — **não mesclar às cegas**; projeto mira Java 17 e Boot 3.

---

## 8. Decisões de produto (resumo — fonte oficial: `docs/questoes-validacao.md`)
- Cadastro: qualquer e-mail, sem aprovação manual, senha mínima 8 (máx. 64), senhas comuns bloqueadas. Campos: nome, e-mail, senha.
- Existe admin da plataforma acima dos laboratórios, separado dos papéis do laboratório; não vê dados dos laboratórios.
- Qualquer usuário confirmado cria laboratório e vira admin dele. Admin da plataforma pode desativar laboratório abusivo.
- Vários admins por laboratório, nunca zero.
- Papéis combináveis (MEMBRO + ADMIN + ORIENTADOR). Só admins do laboratório concedem ADMIN/ORIENTADOR.
- Laboratório desativado: sem alterações nem convites; dados ficam; membros continuam vendo.
- Lista de membros: todos os membros ativos veem nome, e-mail e papel.
- Convite vale 7 dias; reenviar invalida o anterior. Convite direto ao laboratório: admins. Orientador convida só para orientação (e esse convite também coloca a pessoa no laboratório se preciso).
- Pesquisador pode ter vários orientadores. Vínculo é encerrado pelo orientador ou por admin (não pelo pesquisador).
- Quem sai ou é removido perde acesso; dados ficam (somente leitura). Volta só com convite novo.
- Lâminas/contagens (Etapa 2): visíveis ao dono, aos orientadores dele e aos admins do laboratório; os outros membros não veem. Admin não herda permissão de editar contagem.
- Uma célula pode ter mais de uma anormalidade.
- Offline: só lâminas e contagens podem ser criadas sem internet. Guardar também a hora do aparelho, como dado separado e só informativo (a oficial é a do servidor).
- Ainda em aberto (perguntar ao laboratório): limites do lote/meta/casas decimais, "desfazer" entre aparelhos, formato de CSV/PDF, backups legados para testar importação, hospedagem, LGPD.
