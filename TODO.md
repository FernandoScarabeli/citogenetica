# TODO — Sistema de Registro de Lâminas (Citogenética UFLA)

Referências: `docs/Ferramentas de Registro de Lâminas — Proposta de Site (UFLA).pdf` (planejamento, questões para validação e modelagem), manuais e protótipos HTML em `docs/`.

Legenda: `[ ]` a fazer · `[~]` em andamento · `[x]` feito · ⚠️ depende de validação com o laboratório

---

## 0. Infra, git, docs e referências

### Sequência até o primeiro commit (histórico nasce limpo)
Nada é commitado até o fim da sequência.
- [x] **T01 · Segurança + .gitignore**: remover `backend/local.yaml`; `.gitignore` com `node_modules/`, `dist/`, `.impeccable/`, `.taste-skill/` e os PDFs de `docs/` (ficam só locais)
- [x] **T02 · Monorepo**: `backend/` → `apps/api/`, `frontend/` → `apps/web/`; `docker-compose.yml` e `.env.example` na **raiz** (fluxo: clonar → `cp .env.example .env` → `docker compose up -d --build`); restaurar o Maven Wrapper (`.mvn/`) e conferir se o Dockerfile builda. `infra/` só quando houver deploy real.
- [x] **T02b · Front no compose**: serviço `web` no `docker-compose.yml`, pra um único `up` subir tudo; healthcheck também no serviço `api`
- [x] **T02c · Trocar o MinIO por RustFS**: `rustfs/rustfs:1.0.3` (só dev por enquanto; reavaliar versão e backup antes de produção); SDK `io.minio` → AWS SDK v2; bucket **privado** com URLs temporárias assinadas (endpoint público para assinar). O fluxo de domínio das fotos (entidade, status, checksum, permissões) fica para a etapa 6
- [x] **T02d · Dockerfile padrão de mercado**: compilar com a imagem oficial `maven` (tag fixa, Java 17) e rodar com JRE 17; `mvnw` fica intocado para uso local/IDE
- [x] **T03 · Renomear para citogenetica**: pacote `br.ufla.citogenetica`, Maven `citogenetica-api`, `CitogeneticaApplication`, banco/bucket/projeto compose `citogenetica`, containers sem nome fixo, OpenAPI e e-mails sem textos do template, contato removido até haver um oficial, comentário da migration V1 limpo (antes do primeiro commit)
- [x] **T04 · Credenciais e segredos**: API recusa subir com segredo ausente/fraco/de exemplo; `${VAR:?}` no compose; sem admin padrão semeado; sem segredo de recuperação inútil; Mailpit no compose (e-mail de dev); `scripts/gerar-env.sh` gera o `.env` com segredos aleatórios
- [x] **T05 · README básico na raiz**: o que é, estrutura das pastas, como rodar (`cp .env.example .env` + `docker compose up -d --build`)
- [x] **T06 · Licença AGPL-3.0-or-later** (`LICENSE` na raiz, texto oficial da FSF; declarada no `pom.xml` e no `package.json`)
- [x] **T07 · Primeiro commit**: recriar `.git/` na `main`, adicionar uma lista explícita de arquivos, varrer segredos e fazer o commit `chore: inicia repositório`

### Pendências de infra fora da sequência
- [ ] Padrão de branches e commits (Conventional Commits; `feat/`, `fix/`, `docs/`, `chore/`) — documentar no CONTRIBUTING
- [ ] Exemplo: manter como referência do padrão do projeto e deixar isso explícito no código
- [ ] Regra do projeto: migration do Flyway já commitada **nunca** é editada; mudança = nova migration
- [ ] Produção (quando chegar a hora): credencial própria e limitada da API no RustFS, HTTPS, backup do storage, tags de imagem por digest

### Docs
- [ ] Converter o PDF da proposta em Markdown versionado em `docs/`:
  - [ ] `docs/requisitos.md`
  - [x] `docs/arquitetura.md` (monólito modular, módulos, dependências) + ADR 0001
  - [ ] `docs/modelagem.md` + `docs/modelagem/entidades.mmd` (Mermaid citado no doc)
  - [ ] `docs/glossario.md` (termos da citogenética ↔ entidades)
  - [x] `docs/questoes-validacao.md` (com status de cada questão)
- [ ] Mover os protótipos e manuais para `docs/referencias/legado/`
- [ ] Registrar as decisões técnicas já tomadas (ADRs curtas): Java 17, Maven, React, e-mail+senha, UUID

---

## 1. Backend (API, banco e tal)

### 1.1 Correções na base existente
- [x] Recuperação de senha: código com `SecureRandom`, limite de tentativas, invalidar após N erros, rate limit nos endpoints públicos
- [x] Respostas iguais para e-mail existente e inexistente (evitar enumeração)
- [x] Usar `recovery-expiration-minutes` em vez dos 15 minutos fixos
- [x] `atualizarSenha`: só o próprio usuário pode trocar a senha
- [x] `SecurityFilter`: rejeitar usuário inativo ou removido com 401, não 500
- [x] `JWTConfig`: expiração com `Instant.now()` (não se aplica: JWT substituído por sessão)
- [x] Regras de rota: permitir que o usuário edite o próprio perfil
- [x] Decidir entre JWT em header e sessão com cookie HttpOnly + CSRF (sugerido no planejamento)
- [x] CORS único, com as origens vindas do `.env`
- [x] `CriteriaCreator`: campo inválido → 400; criar a extensão `unaccent` na migration
- [x] `show-sql` só em dev; actuator sem `show-details: always` em produção
- [x] Ajustar `GlobalExceptionHandler` para os casos novos

### 1.2 Notificações e SSE (arrumar, não remover)
- [x] `notificacao.usuario_id` + filtrar todas as consultas pelo usuário logado
- [x] `marcarComoLida`: conferir o dono
- [x] Emitters por usuário (`Map<usuarioId, List<SseEmitter>>`), com publish só para o destinatário
- [x] Publicar depois do commit (`@TransactionalEventListener(AFTER_COMMIT)`)
- [x] Autenticar o SSE sem token na URL (cookie ou ticket curto de uso único)
- [x] `/subscribers/count` só para admin, ou remover
- [~] ⚠️ Definir quais eventos geram notificação (comentário, conflito...) — convites de laboratório e de orientação já notificam

### 1.3 Base do domínio
- [x] Monólito modular: pacotes por módulo (`usuario`, `notificacao`, `exemplo`) + `shared`, `config`, `infra`, com regras verificadas por ArchUnit
- [x] **Primeira tarefa depois do primeiro commit: auto-cadastro com confirmação de e-mail** (sem isso ninguém cria conta, porque o admin padrão foi removido na T04)
- [x] `./mvnw test` precisa passar num clone limpo, sem `.env` nem banco rodando: perfil de teste + Postgres via Testcontainers no teste de contexto (hoje ele exige variáveis de e-mail e banco no ar)
- [x] IDs UUID nas entidades novas (gerados no cliente quando vierem offline) — ADR 0003
- [x] Papel por laboratório (Participação), não global no usuário
- [~] Admin global da plataforma (acima dos laboratórios): primeiro admin por comando de uso único (ADR 0002) e desativar/reativar laboratório feitos; listagem global de laboratórios e painel feitos; faltam parâmetros do sistema
- [x] Auto-cadastro + verificação de e-mail (e troca de e-mail com confirmação no endereço novo)
- [x] Testes de integração com Testcontainers + OpenAPI atualizado a cada etapa

### 1.4 Etapas da API (seguindo o planejamento)
- [x] **Etapa 1 · Base**: Laboratório, Participação (ATIVA/SAIU/REMOVIDA), Convite (hash do token, expiração, aceite atômico), Vínculo de orientação, regra do último administrador; `LaboratorioAccessQuery` pronta para as lâminas da Etapa 2
- [ ] **Etapa 2 · Contagem**: Protocolo, Versão (imutável quando publicada), Fase (`conta_como_divisao`), Tipo de anormalidade (fixo e personalizado, normalização), Lâmina, Registro (`quantidade`, `registrado_em`, `fuso_registro`, exclusão lógica, versão), cálculos oficiais (totais, %, IM), desfazer, edição, histórico; seed de meiose e mitose
- [ ] **Etapa 3 · Dados**: exportação JSON versionada, importador dos backups legados (sem duplicar), CSV (resumo e uma linha por registro), PDF
- [ ] **Etapa 4 · Offline/sync**: operações idempotentes (`operacao_id` + hash do payload), revisões, conflitos, consulta incremental com cursor
- [ ] **Etapa 5 · Pesquisa**: Projeto, Experimento, editor de protocolos configuráveis
- [ ] **Etapa 6 · Colaboração**: Fotos (MinIO privado, upload em duas fases, checksum), Comentários
- [ ] **Etapa 7 · Análise**: comparações e gráficos ⚠️ (fórmulas e agregações a validar)

---

## 2. Frontend

### 2.1 Correções no que já existe
- [~] Papéis sem hierarquia: admin não herda permissão de editar contagens (o planejamento é explícito) — papéis de laboratório já vêm da API e são combináveis; falta aplicar às contagens (Etapa 2)
- [ ] Histórico por dia + edição de registro (o modal existe, mas nada abre ele)
- [ ] "Desfazer" sem apagar registros em sequência depois de recarregar (limite e/ou confirmação)
- [ ] Lote como **1 registro com `quantidade`**, e não N registros
- [ ] Importação mesclando por id de registro (não substituir a lâmina inteira) + prévia de conflitos
- [ ] Escopo das anormalidades personalizadas: usuário + laboratório + fase
- [ ] Excluir lâmina: tratar falha do `commit`
- [ ] CSV: escapar células que começam com `=`, `+`, `-` ou `@`
- [ ] "Criada por" com dado real

### 2.2 Integração com a API
- [~] Camada `api/` substituindo `storage.ts`, `laboratories.ts` e `demoUser` — `laboratories.ts` e `demoUser` removidos; `storage.ts` (lâminas) segue local até a Etapa 2
- [x] Telas de cadastro, login, recuperação de senha e verificação de e-mail (e Minha conta: nome, senha, troca de e-mail)
- [x] Laboratórios, convites e membros reais (e orientação)
- [ ] Contagem, lâminas, resumo e IM vindos da API
- [x] Notificações via SSE no sino
- [x] Painel do admin da plataforma: lista, busca, filtro, desativar e reativar laboratório
- [ ] Importador dos backups dos HTMLs antigos (meiose e mitose)

### 2.3 PWA e offline (Etapa 4)
- [ ] Manifest, service worker, IndexedDB
- [ ] Fila persistente de operações + indicador de estado ("Salvo neste aparelho: N pendentes")
- [ ] Tela de resolução de conflitos
- [ ] Sair da conta com pendências (continuar, exportar ou sair mantendo os dados)

### 2.4 Etapas 5–7 no front
- [ ] Projetos e experimentos, editor de protocolos
- [ ] Fotos e comentários
- [ ] Comparações, gráficos, atalhos de teclado

---

## 3. Finalização open source
- [ ] README completo (prints, rodar local sem Docker, arquitetura); a versão básica entra na T05
- [ ] `CONTRIBUTING.md`: setup, padrão de branch e commit, como rodar os testes, fluxo de PR
- [ ] `CODE_OF_CONDUCT.md`, `SECURITY.md` (como reportar vulnerabilidades)
- [ ] Templates de issue e PR (`.github/`)
- [ ] CI (GitHub Actions): build + testes da API e do web, lint
- [ ] Seção de autoria e mantenedores no README
- [ ] `CHANGELOG.md` e versionamento (SemVer)
- [ ] Guia de deploy (⚠️ hospedagem UFLA/DGTI ou serviço externo) + política de backup do banco
- [ ] LGPD: termo de uso, política de privacidade, exportar e excluir a própria conta

---

## ⚠️ Pendências com o laboratório (bloqueiam partes acima)
Ver `docs/questoes-validacao.md`. As principais:
- [ ] Visibilidade das lâminas entre pesquisadores
- [ ] Quem cria o vínculo orientador ↔ pesquisador; se pode haver mais de um orientador
- [ ] Uma célula pode ter mais de uma anormalidade?
- [ ] Limites do lote, meta configurável, casas decimais
- [ ] Alcance do "desfazer" entre aparelhos
- [ ] Backups reais (ou fictícios) dos HTMLs antigos para testar a migração
- [ ] Formato de CSV e PDF esperado (Excel, R, SISVAR)
- [ ] Hospedagem e responsável pela manutenção
