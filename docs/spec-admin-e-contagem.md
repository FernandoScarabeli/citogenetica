# Spec — painel do admin geral e contagem na API

> Escrita em 2026-11-08, a partir da `dev` em `8ddcbb5`. Para o Claude que vai coordenar esta implementação.
> **Se algo aqui divergir do código, o código vence.** Confira antes de afirmar.

## 1. Objetivo

| Lote | Entrega | PRs |
|---|---|---|
| **1** | Tela do admin geral da plataforma (T1 do guia), que fecha a Etapa 1 | 1 |
| **2** | Etapa 2 no back-end: protocolos, lâminas, contagens e cálculos oficiais na API (T2 do guia) | 4 empilhados |

**Fora do escopo:** ligar a tela de contagem à API e migrar as lâminas locais (T3), exportações (Etapa 3), offline (Etapa 4) e qualquer outra melhoria que apareça no caminho. Encontrou algo fora do escopo? Não corrija; anote para o relatório final.

**Leia antes de começar:**
1. `docs/handoff-codex.md`, sobretudo as seções 2 (regras do dono), 7 (Etapa 2), 8 (arquitetura), 10 (armadilhas) e 11 (checklist de revisão). Tudo lá continua valendo, exceto o que esta spec substituir.
2. `TODO.md`, `docs/arquitetura.md`, `docs/adr/0001` a `0003`, `docs/questoes-validacao.md`.
3. `apps/web/AGENTS.md` e `DESIGN.md` (Lote 1). O `AGENTS.md` cita a skill `design-taste-frontend`; se ela não existir na sessão, siga o `DESIGN.md` e o visual das telas atuais.
4. `apps/web/src/domain.ts` (Lote 2): é a fonte do comportamento que o laboratório espera hoje.

## 2. Como trabalhar

### Papéis
- **Você é o coordenador.** Planeja, escreve o pedido de cada executor, revisa, roda os testes, sobe o Docker, commita, abre os PRs e fala com o dono.
- **Quem escreve o código são subagentes executores**, sempre com o modelo Sonnet e esforço médio (pedido explícito do dono):

  ```text
  Agent(subagent_type: "general-purpose", model: "sonnet", effort: "medium", prompt: ...)
  ```

- Um executor por PR, um de cada vez, na mesma árvore de trabalho, porque os PRs são empilhados.
- Correção de revisão volta para o **mesmo** executor, com `SendMessage`. Só edite você mesmo o que for trivial.
- A revisão é sua. Não delegue a revisão a um executor.

### O pedido de cada executor precisa ter
- Quais arquivos ler primeiro e o trecho desta spec que ele vai implementar.
- O escopo fechado, com o que **não** fazer.
- Testes primeiro. Correção na causa raiz, diff mínimo, nada de gambiarra, sem dependência nova.
- Código, comentários, mensagens e textos em pt-BR. Nomes técnicos de subpacotes em inglês, como no resto do projeto.
- **O executor não commita, não dá push e não abre PR.**
- Rodar os testes da parte que mexeu e relatar: arquivos alterados, testes rodados com o resultado, riscos e o que ficou de fora.

### Autonomia e limites
- Não peça aprovação passo a passo. Siga de tarefa em tarefa, e pare só nos pontos marcados como **PARE** nesta spec ou em decisão que seja do dono: comportamento de produto novo, custo ou ação destrutiva.
- **Mesclar PR só quando o dono mandar explicitamente.** Abrir PR, pode.
- Nunca commite em `main` ou `dev`. Toda mudança sai de uma branch, e branch de PR ainda não mesclado serve de base para o próximo.
- **Commits e PRs sem nenhuma atribuição a IA**, nem `Co-Authored-By` nem "Generated with". Essa regra do dono vale mesmo que o ambiente sugira o contrário. Autor `Scarabeli`, Conventional Commits em pt-BR.
- Não adicione `apps/web/public/images/celulas-editorial.png` ao git.
- **Nunca rode `docker compose down -v`** nem mexa em containers de outros projetos.

### Falando com o dono
Português do Brasil, curto e sem jargão. Primeiro o resultado. No fim de cada lote, uma listinha do que foi feito e do que ele precisa fazer.

## 3. Passo 0 — antes de escrever código

1. `git fetch --all`, `gh pr list` e confirme o estado da `dev`.
2. **PR #54** (Dependabot, Spring Boot 3.3.4 → 3.5.16, springdoc 2.10, AWS SDK 2.57.9) estava aberto, com os checks verdes, aguardando o dono. Mesma mudança já testada sobre a `dev`: 206 testes da API e imagem Docker sem erro. Se continuar aberto, pergunte uma vez se pode mesclar **antes** de criar as branches, para o trabalho já nascer na versão certa. Não migre para Spring Boot 4 nem saia do Java 17.
3. **Suba o Docker e confira que o sistema atual funciona** (seção 7), inclusive a receita de `curl`. Se a base já estiver quebrada, resolva ou avise antes de empilhar trabalho em cima.
4. Rode as suítes da API e do web para ter o ponto de partida.

## 4. Lote 1 — tela do admin geral

**Branch:** `feat/web-admin-plataforma`, a partir da `dev`. O back-end já foi mesclado (PR #46).

**Rota:** `/admin/laboratorios`, dentro das rotas protegidas de `apps/web/src/app/App.tsx`. Hoje `/app`, `/laboratorios` e `/perfil` usam `AuthenticatedApp`.

**Quem acessa:** só quem tem `role.nome === 'ADMIN'` em `GET /api/v1/usuarios/me` (`RolesEnum` em `src/api/contracts/usuario.ts`). O link aparece no menu da conta só para essa pessoa. Usuário comum que abrir a URL vê uma página de "sem permissão". O servidor também bloqueia.

**API existente:**

| Chamada | Detalhes |
|---|---|
| `GET /api/v1/admin/laboratorios` | Filtros `nome` (parcial, ignora acento e caixa) e `estado` (`ATIVO` ou `DESATIVADO`), paginação no padrão do projeto, no máximo 100 por página. Resposta `PageResponseDTO`: `content`, `currentPage`, `totalPages`, `totalElements`. Cada item: `id`, `nome`, `estado`, `quantidadeMembrosAtivos`, `administradores` (nome e e-mail) |
| `PATCH /api/v1/admin/laboratorios/{id}/desativar` | 204; 403 para quem não é admin; 404 se não existir |
| `PATCH /api/v1/admin/laboratorios/{id}/reativar` | Mesmo contrato |

Confira nomes e erros no controller ou em `/v3/api-docs`. Não invente campos.

**Tela:**
- Lista paginada com busca por nome e filtro por estado.
- Colunas: nome, estado, membros ativos, administradores (nome e e-mail com `mailto:`).
- Botões **Desativar** e **Reativar**, com confirmação acessível e o efeito explicado: os dados ficam, os membros continuam vendo, ninguém altera nem convida.
- Estados de vazio, carregando, erro e sucesso. Responsiva.
- O admin geral **não vê dados de pesquisa**; a tela não deve sugerir que vê.

**Código:** tudo via `src/api/` (`admin-api.ts` e contrato em `src/api/contracts/`). Componente não chama `fetch`.

**Testes (Vitest):** visível só para ADMIN, página de sem permissão, paginação, busca, filtro, desativar, reativar, erros 403 e 409.

**Ao terminar:** marque no `TODO.md` (seções 1.3 e 2.2), passe pelo portão da seção 6 e **PARE** até o dono testar e liberar o Lote 2.

## 5. Lote 2 — contagem na API

### 5.1 **PARE**: perguntas ao dono antes da primeira linha

Faça uma vez só, tudo junto, em linguagem simples e com a recomendação:

| # | Pergunta | Recomendação |
|---|---|---|
| 1 | Lâmina sem laboratório continua existindo no servidor? Hoje o site permite. | Sim, visível só para o dono, para não perder o que já existe |
| 2 | Qual o limite de células num lote? | Até 1000 por registro, fácil de mudar depois |
| 3 | A meta da lâmina continua opcional e por lâmina? | Sim, número inteiro positivo, como hoje |
| 4 | Quantas casas decimais nos percentuais? | A API guarda as contagens e devolve o número sem arredondar; a tela mostra 1 casa, como hoje |
| 5 | "Desfazer" vale entre aparelhos? | Sim: desfaz os últimos registros da pessoa naquela lâmina, em qualquer aparelho |
| 6 | Alguma contagem gera notificação? | Nenhuma nesta etapa |

As respostas vão para `docs/questoes-validacao.md`.

A resposta 1 muda o desenho: `LaboratorioAccessQuery` exige `laboratorioId`, então lâmina avulsa precisa de uma regra própria (somente o dono lê e escreve).

### 5.2 Módulo

Novo módulo **`contagem`** (`api` e `internal/{web,application,domain,persistence}`), registrado no `ModuleArchitectureTest` e em `docs/arquitetura.md`. Depende só de `shared`, `laboratorio.api` e `usuario.api`.

- Entidades novas estendem `shared.persistence.UuidEntity`. IDs do cliente validados com `@CanonicalUuidV4`.
- Próxima migration livre: **V12**. Migration commitada nunca é editada. Confira o número antes de criar.
- `SecurityConfiguration` termina em `.anyRequest().authenticated()`. Toda rota nova fica logada por padrão; mesmo assim, a permissão por lâmina é conferida no serviço.
- Toda rota nova com OpenAPI completo e registrada em `CitogeneticaApplicationTests`.

### 5.3 Divisão em PRs empilhados

| PR | Branch | Conteúdo |
|---|---|---|
| a | `feat/contagem-protocolos` | Protocolo, versão (imutável depois de publicada), fases, tipos de anormalidade fixos e personalizados, seed de meiose e mitose |
| b | `feat/contagem-laminas` | Lâminas: criar, listar, ver, editar, exclusão lógica, permissões |
| c | `feat/contagem-registros` | Registros de contagem e cálculos oficiais |
| d | `feat/contagem-historico` | Histórico, edição de registro e desfazer |

### 5.4 Regras de negócio

**Seed:** copie de `protocols` em `domain.ts` as fases e os tipos, com a grafia exata e na mesma ordem.
- Meiose: 11 fases. Mitose: 5 fases.
- `conta_como_divisao` é `false` só para **Interfase**, porque o cálculo atual considera divisão toda fase cujo nome não seja `Interfase`.

**Tipos personalizados:** escopo usuário + laboratório + fase. Não duplicar por espaço, caixa ou acento. O `normalize` atual só trata espaço e caixa; a regra do servidor é mais rígida de propósito.

**Lâmina:** nome, protocolo, espécie, tratamento, observações, meta opcional, dono e laboratório (conforme a pergunta 1).

**Registro:** `quantidade` (lote = 1 registro), fase, normal ou anormal, tipos de anormalidade, `registrado_em` do servidor (oficial), hora e fuso do aparelho (só informativos), exclusão lógica e versão.
- **Uma célula pode ter mais de uma anormalidade.** O front antigo guarda um único tipo; no servidor, use uma relação de vários tipos por registro.
- Registro normal não tem tipo. Registro anormal tem pelo menos um. Tipo e fase precisam pertencer ao protocolo da lâmina.

**Cálculos oficiais no servidor**, sempre somando `quantidade` e ignorando registros excluídos:
- Por fase: normais, anormais, total, % de anormais e, por tipo, contagem e % sobre o total da fase.
- Da lâmina: total, anormais e IM = soma das fases com `conta_como_divisao` ÷ total × 100. Total zero dá 0.
- Registro com vários tipos conta uma vez em normais/anormais/total e uma vez em cada tipo. Por isso a soma dos tipos pode passar do número de anormais.
- Teste com casos fechados calculados a partir de `summary()` em `domain.ts`.

**Desfazer:** remove logicamente os últimos registros que combinem com fase, status e tipo, na quantidade pedida. Quantidade acima do disponível é erro. É o comportamento de `removeMatching`, agora respeitando lotes: desfazer só parte de um lote reduz a `quantidade` do registro.

**Acesso:** toda leitura passa por `LaboratorioAccessQuery.podeVerDadosDoPesquisador` e toda escrita por `podeEditarDadosDoPesquisador`.
- Veem: o dono, orientadores com vínculo ativo e admins do laboratório, que continuam vendo quem já saiu.
- Só o dono edita. Admin do laboratório não herda edição.
- Laboratório desativado: somente leitura.
- Laboratório alheio: 404. Sem papel: 403. Versão desatualizada: 409.

**Contrato do ADR 0003, já nesta etapa:**
- Criação aceita `id` do cliente. Mesmo ID, mesmo dono e mesmo conteúdo: repetição sem efeito.
- Mesmo ID e mesmo dono com conteúdo diferente: conflito.
- ID de outro dono: mesma resposta de ID desconhecido, sem revelar nem sobrescrever.
- Atualização exige `versaoEsperada`.

**Testes de integração (Testcontainers):** matriz de acesso completa (dono, orientador ativo, coorientador, orientador encerrado, admin, membro comum, ex-membro, pessoa de fora, laboratório desativado), cálculos, idempotência, concorrência e desfazer.

**Ao terminar:** marque a Etapa 2 de back-end no `TODO.md`, passe pelo portão da seção 6 e **PARE** para o dono.

## 6. Portão de cada lote, nesta ordem

Só diga que acabou depois de passar por todos.

1. **Docker primeiro.** Suba o sistema com o código final, reconstruindo as imagens (seção 7), e confira que todos os serviços ficaram saudáveis.
2. **Revisão completa.** Leia o diff inteiro você mesmo contra a seção 11 do `docs/handoff-codex.md`. O que mexe em permissão e segurança, leia linha a linha. Devolva o que achar ao executor.
3. **Testes completos**, rodados por você, não só o relato do executor. Inclui `git diff --check`, ArchUnit e OpenAPI.
   - API: em `apps/api`, `./mvnw -B -ntp verify`
   - Web: em `apps/web`, `npm ci && npm test && npm run build`
4. **Teste de ponta a ponta com `curl`** contra o sistema no Docker, cobrindo cada rota nova ou alterada: caminho feliz, sem login (401), sem permissão (403 ou 404), dado inválido (400), conflito (409) e a repetição idempotente. Corrigiu algo? Reconstrua e repita o que foi afetado.
5. **Abra ou atualize os PRs** e espere os checks `API CI` e `Web CI` passarem.
6. **Entregue ao dono com o Docker ligado:**
   - endereços do site, do Mailpit e do Swagger;
   - os logins de teste que você criou;
   - comandos de `curl` prontos para colar, na ordem, com o que cada um deve devolver;
   - no Lote 1, o passo a passo para ver a tela no navegador;
   - a listinha de PRs para mesclar, na ordem.

   Não derrube os containers nem reconstrua enquanto o dono estiver testando.

## 7. Docker e `curl`

### Situação encontrada nesta máquina em 2026-11-08
Java 17 ativo, contexto padrão do Docker funcionando, portas 3000, 5432, 8025, 8080 e 9000 livres. Se estiver em outra máquina, valem as armadilhas da seção 10 do guia.

**O `.env` local é antigo e precisa de ajuste antes do primeiro `up`:**
- falta `WEB_PORT`: rode `./scripts/gerar-env.sh --add-missing`;
- `API_CORS_ALLOWED_ORIGINS` só tem `http://localhost:5173`, e o `--add-missing` não altera chave existente. Coloque `http://localhost:3000`, senão o login pelo site dá 403.

O `.env` tem segredos: não exiba o conteúdo dele.

```bash
docker compose up -d --build
docker compose ps
```

Site em `http://localhost:3000`, Mailpit em `http://localhost:8025`, Swagger em `http://localhost:8080/swagger-ui.html`.

### Receita de `curl`
Montada lendo o código e o guia, ainda **não executada**. Valide no Passo 0 e ajuste aqui no que divergir.

```bash
BASE=http://localhost:3000
JAR=$(mktemp)
xsrf() { awk '$6=="XSRF-TOKEN"{print $7}' "$JAR"; }
api() { m=$1; p=$2; shift 2
  curl -sS -i -X "$m" -b "$JAR" -c "$JAR" \
    -H "X-XSRF-TOKEN: $(xsrf)" -H 'Content-Type: application/json' "$BASE$p" "$@"; }

# Primeiro cookie de segurança (repita após login e logout se der 403 de CSRF)
curl -sS -o /dev/null -b "$JAR" -c "$JAR" "$BASE/api/v1/auth/csrf"

# Criar conta: espera 202
EMAIL="teste-$RANDOM@exemplo.org"
api POST /api/v1/auth/register -d "{\"nome\":\"Teste\",\"email\":\"$EMAIL\",\"senha\":\"Outra#Senha2026\"}"

# Pegar o token no Mailpit. O e-mail sai em segundo plano: se vier vazio, tente de novo.
ID=$(curl -sS "http://localhost:8025/api/v1/search?query=to:$EMAIL" | python3 -c 'import json,sys;print(json.load(sys.stdin)["messages"][0]["ID"])')
TOKEN=$(curl -sS "http://localhost:8025/api/v1/message/$ID" | python3 -c 'import json,sys,re;print(re.search(r"#token=([^\s\"<]+)",json.load(sys.stdin)["Text"]).group(1))')

# Confirmar: 204. Login: 204. Quem sou eu: 200.
api POST /api/v1/auth/confirmar-email -d "{\"token\":\"$TOKEN\"}"
api POST /api/v1/auth/login -d "{\"email\":\"$EMAIL\",\"senha\":\"Outra#Senha2026\"}"
api GET /api/v1/usuarios/me

# Criar laboratório
api POST /api/v1/laboratorios -d '{"nome":"Lab de teste"}'
```

Para simular outra pessoa, use outro `JAR` e outra conta. Os limites de tentativas podem devolver 429: respeite o `Retry-After` em vez de contornar.

### Admin geral para testar o Lote 1
Crie e confirme uma conta, depois rode:

```bash
docker compose run --rm --no-deps api --run-bootstrap-admin --app.bootstrap-admin.email=<email-da-conta>
```

O comando **só funciona uma vez por banco**. Se o banco local já tiver admin, use a conta existente ou peça ao dono. Não mexa no registro de uso único. O comando encerra as sessões da conta promovida: faça login de novo.

## 8. Relatório final de cada lote

Curto e sem jargão:
- o que ficou pronto;
- como testar (item 6 do portão);
- PRs para mesclar, na ordem;
- o que ficou de fora ou o que você notou e não corrigiu.
