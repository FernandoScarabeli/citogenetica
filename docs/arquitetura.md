# Arquitetura da API

## Decisão

A API é um monólito modular leve: um deploy e um banco PostgreSQL, organizados por assunto. Cada módulo mantém suas camadas internas e, quando precisa ser consumido por outro módulo, publica contratos pequenos em `api`. Controllers, casos de uso, domínio, persistência e mappers ficam em `internal`. A raiz `config` compõe a aplicação; `infra` contém adaptadores técnicos; `shared` reúne contratos e utilitários realmente comuns.

Os módulos de domínio usam nomes em português (`usuario`, `notificacao`, `exemplo`); subpacotes técnicos são minúsculos e em inglês. A classe `CitogeneticaApplication` permanece na raiz `br.ufla.citogenetica` para habilitar o escaneamento Spring da aplicação.

## Dependências permitidas

`A → B` significa que A depende de B. Dependências entre módulos de negócio passam somente por `api`. `shared` não depende de módulos, `config` ou `infra`; módulos de negócio usam portas de `shared`, nunca adaptadores de `infra`. `infra` pode depender de `shared`, mas não de módulos de negócio. `config` usa contratos públicos de módulos e compõe os beans da aplicação. O grafo deve permanecer acíclico.

```text
config          → shared, usuario.api
usuario         → shared
notificacao     → shared, usuario.api, laboratorio.api
exemplo         → shared
laboratorio     → shared, usuario.api
contagem        → shared, usuario.api, laboratorio.api
infra.email     → shared
infra.storage   → shared
shared          → (nenhum módulo)
```

`notificacao` não precisa de pacote `api` próprio: nenhum outro módulo consome seus serviços. Sua entrega SSE e seus controllers são detalhes internos. O módulo usa `UsuarioExistenceQuery` e `UsuarioConviteQuery` em `usuario.api` para preservar a validação do destinatário sem conhecer entidades ou repositórios de usuário, e consome os eventos públicos de convites de laboratório e de orientação publicados por `laboratorio` após o commit.

O planejamento prevê protocolo, contagem, dados/importação/exportação, sincronização offline, pesquisa/projetos, colaboração/fotos/comentários e análise. Ao serem implementados, devem publicar eventos ou fachadas públicas e manter o grafo sem ciclos.

## Responsabilidades e pacotes

```text
br.ufla.citogenetica
├── config
│   ├── openapi
│   ├── properties
│   ├── secret
│   ├── security
│   └── web
├── contagem
│   └── internal/{application,domain,persistence,web}
├── exemplo
│   └── internal/{application,domain,mapper,persistence,web}
├── infra
│   ├── email
│   └── storage
├── laboratorio
│   ├── api/{LaboratorioAccessQuery,event}
│   └── internal/{application,domain,persistence,web}
├── notificacao
│   └── internal/{application,domain/event,mapper,persistence,sse,web}
├── shared
│   ├── email
│   ├── filter
│   ├── mapper
│   ├── pagination
│   ├── persistence
│   ├── secret
│   └── web/{dto,error}
└── usuario
    ├── api/{error,event}
    └── internal/{application,domain/event,mapper,persistence,security,web}
```

- `usuario`: conta, autenticação, perfil, papéis globais e recuperação. O papel global de administrador da plataforma é separado dos papéis por laboratório.
- `notificacao`: notificações do destinatário e entrega em tempo real por SSE.
- `exemplo`: CRUD vertical mantido como padrão de referência para novos módulos.
- `laboratorio`: laboratórios, participações, papéis locais, convites e vínculos de orientação. O papel global `usuario.ADMIN` não concede acesso a laboratórios. Convites são enviados após commit; notificações recebem somente o evento público sem token de ação. `LaboratorioAccessQuery` publica as consultas de leitura e edição de dados do pesquisador para consumidores como lâminas. A leitura permanece disponível em laboratório desativado; edição exige laboratório ativo e participação do próprio pesquisador. A saída ou remoção encerra seus vínculos ativos de orientação, e o papel `ORIENTADOR` não pode ser retirado enquanto houver vínculo ativo.
- `contagem`: protocolos versionados (meiose e mitose), fases e tipos de anormalidade fixos e personalizados. Versão publicada, suas fases e seus tipos fixos são imutáveis por triggers do banco; novos protocolos e versões entram por migration, sem rota de escrita. O módulo ainda não publica `api`. Tipos personalizados são do usuário, por fase e escopo (laboratório ou lâmina avulsa), com criação idempotente pelo id do cliente (ADR 0003) e nome único ignorando espaços, caixa e acentos (`NomeTipoAnormalidade`). Acesso a laboratório usa `LaboratorioAccessQuery`; o papel global `ADMIN` não concede acesso. Lâminas, registros e cálculos virão em PRs seguintes.
- `config`: segurança HTTP, sessão, CSRF, CORS, proxy confiável, OpenAPI e tratamento global de erros.
- `infra.email` e `infra.storage`: adaptadores SMTP e RustFS/S3. O envio de e-mail é exposto por `shared.email.EmailSender`.
- `shared`: paginação, filtros, mappers genéricos, persistência comum, erros HTTP, segredos e portas técnicas compartilhadas.

## Contratos e eventos

`usuario.api` publica `UsuarioPrincipal`, `RolesEnum`, as exceções de usuário traduzidas pelo handler global, `UsuarioExistenceQuery`, `UsuarioRosterQuery`, `UsuarioConviteQuery` e eventos de sessão. As consultas são implementadas em `usuario.internal` sobre o repositório interno e retornam somente projeções, nunca entidades JPA. O módulo `laboratorio` mantém IDs de usuário como `BIGINT` escalar e filtra por participação ativa; ele não depende de tipos internos de usuário. `laboratorio.api.event` publica eventos pós-commit de convite sem token; `notificacao` consulta `usuario.api` e cria as notificações em novas transações. `laboratorio.api.LaboratorioAccessQuery` fornece aos módulos consumidores as decisões de leitura e edição de dados sem expor entidades ou repositórios. A notificação grava `destinatarioId` como ID escalar; a coluna `usuario_id`, a FK `fk_notificacao_usuario` e o `ON DELETE CASCADE` existentes permanecem. A FK protege a integridade no banco e a consulta pública preserva a resposta de usuário inexistente.

`config.security` publica `SessaoEncerradaEvent` no logout. O listener interno de `notificacao` fecha apenas os emissores associados à sessão do evento. A requisição de logout não tem transação; o listener usa fallback síncrono e o handler de logout é executado antes de `SecurityContextLogoutHandler` invalidar a sessão. Se houver uma transação ativa, o listener processa após o commit. Revogação de outras sessões também é tratada após o commit. Eventos de criação de notificação seguem publicados somente após o commit.

O `GlobalExceptionHandler` fica em `config.web` para manter um formato HTTP comum. Erros compartilhados ficam em `shared.web.error`; constraints específicas de laboratório e de contagem são traduzidas dentro de `laboratorio.internal.web` e `contagem.internal.web` para conflito genérico `409`, sem expor nomes de constraints.

## Persistência, configuração e segurança

`@SpringBootApplication` e `@ConfigurationPropertiesScan` permanecem na raiz. Prefixos de propriedades e YAML não mudam. Migrations já aplicadas são imutáveis; como a coluna e a FK da notificação não mudam, a troca da associação JPA por ID escalar não exige migration. `ddl-auto=validate` deve continuar validando o schema.

`UsuarioPrincipal` é serializado na sessão JDBC. A mudança de pacote feita no módulo `usuario` também mudou o nome totalmente qualificado da classe; sessões existentes com o formato anterior precisam ser invalidadas antes do deploy. Em desenvolvimento, basta autenticar novamente.

## Verificação automática

ArchUnit, somente em escopo de teste, verifica que módulos de negócio acessam outros módulos apenas por `api`, que `shared` não depende de módulos/config/infra, que nenhum módulo ou adaptador depende de `config`, que `infra` não depende de negócio, que negócio não depende de `infra`, que `config` usa somente APIs públicas e que não há ciclos. O teste também rejeita novas raízes de pacote fora de `config`, `shared`, `infra`, `usuario`, `notificacao`, `exemplo`, `laboratorio`, `contagem` e da raiz de inicialização. Um teste de integração compara todos os paths publicados em `/v3/api-docs`.
