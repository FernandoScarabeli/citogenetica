# Arquitetura da API

## Decisão

A API é um monólito modular leve: um deploy e um banco PostgreSQL, com pacotes por assunto e camadas internas. Cada módulo publica contratos pequenos em `api`; controllers, casos de uso, domínio, repositórios e mappers ficam em `internal`. Um módulo não importa entidades, repositórios ou outros detalhes internos de outro módulo.

Os nomes de módulos de domínio são em português (`usuario`, `notificacao`, `exemplo`, `laboratorio`, `contagem`). Subpacotes técnicos são sempre minúsculos e em inglês: `api`, `internal`, `web`, `application`, `domain`, `persistence`, `mapper`. A infraestrutura compartilhada fica em `shared` (`web`, `persistence`, `mapper`, `pagination`, `filter`). A composição Spring fica em `config`; adaptadores externos ficam em `infra.email` e `infra.storage`.

## Módulos e dependências permitidas

`A → B` significa que A pode usar somente a API pública de B. `config` é a raiz de composição: pode conectar módulos e infraestrutura, mas nenhum módulo nem adaptador pode depender dela. `shared` não depende de módulos de negócio, de `config` ou de `infra`. Módulos de domínio não importam adaptadores concretos; consumidores legados serão migrados para portas técnicas públicas junto dos módulos nos próximos PRs. O grafo precisa permanecer acíclico.

```text
usuario        → shared
notificacao    → shared, laboratorio.api.event, contagem.api.event, colaboracao.api.event
exemplo        → shared
laboratorio    → shared, usuario.api
protocolo      → shared
contagem       → shared, laboratorio.api, protocolo.api
dados          → shared, laboratorio.api, protocolo.api, contagem.api
sincronizacao  → shared, laboratorio.api, contagem.api
pesquisa       → shared, laboratorio.api, protocolo.api
colaboracao    → shared, laboratorio.api, contagem.api
analise        → shared, contagem.api, protocolo.api, pesquisa.api
```

`notificacao` consome eventos públicos dos módulos produtores de convites, contagens e colaboração (`laboratorio`, `contagem`, `colaboracao`). Os produtores não dependem de `notificacao`, evitando ciclos. Consultas/comandos que precisem de resposta imediata usam uma fachada pública. Eventos representam fatos já ocorridos, são imutáveis, não carregam entidades JPA e são tratados após commit quando houver efeito externo.

`infra.email` e `infra.storage` concentram os adaptadores SMTP e RustFS/S3 e suas propriedades. Os utilitários de segredo (`SecretValue`, `@ValidSecret` e validador) ficam em `shared.secret` porque são usados pela configuração da aplicação e pelos dois adaptadores; o conversor Spring fica em `config.secret`. Prefixos de propriedades não mudam. Templates e intenção dos e-mails pertencem ao módulo que inicia cada fluxo; o template e o listener de recuperação continuam no legado até o PR 3. Até lá, esse listener legado ainda chama o adaptador SMTP diretamente.

## Responsabilidades

- `usuario`: conta, autenticação, perfil e recuperação de senha. O administrador global da plataforma continua existindo para parâmetros do sistema e futuro painel; papéis por laboratório pertencem a `laboratorio.Participacao` e não são herdados do papel global.
- `notificacao`: caixa de notificações e entrega SSE por usuário.
- `exemplo`: CRUD vertical de referência para novos módulos.
- `laboratorio`: laboratório, participação, convite e vínculo de orientação.
- `protocolo`: protocolo, versão, fase e tipo de anormalidade.
- `contagem`: lâmina, registros, cálculos e histórico.
- `dados`: exportação e importação, inclusive backups legados.
- `sincronizacao`: operações idempotentes, revisões, conflitos e cursor offline.
- `pesquisa`: projetos, experimentos e configuração de protocolos de pesquisa.
- `colaboracao`: fotos e comentários.
- `analise`: comparações e gráficos.

## Infraestrutura e persistência

`config` reúne segurança HTTP, sessão, CSRF, CORS, proxy confiável, OpenAPI, SSE e configuração global. As propriedades de banco ficam em `config.properties`; propriedades de e-mail e storage ficam junto dos adaptadores. `SecurityConfiguration` ainda compõe temporariamente `SessionUserValidationFilter` e `UsuarioPrincipal` do legado e chama `EventPublisherService` no logout. O acoplamento direto ao SSE será substituído por evento de sessão no PR 4, sem alteração neste PR. Rate limit e executor da recuperação continuam no legado para o PR 3.

O contrato HTTP de erro, `NotFoundException` e `BusinessException` compartilhada ficam reunidos em `shared.web.error`; `FiltroInvalidoException` permanece em `shared.filter`. O handler global fica em `config.web`. Ele ainda mapeia exceções específicas de autenticação/perfil que estão no legado; essas exceções acompanham `usuario` no PR 3, sem dependência de `shared` para negócio.

`CriteriaCreator` e `CamposPermitidos` ficam em `shared.filter`; `PageableFactory`, constantes e resposta paginada em `shared.pagination`; contratos e interfaces de mapper genérico em `shared.mapper`; auditoria/versionamento comum em `shared.persistence`. A base atual usa `IDENTITY` para IDs numéricos. `GenericDTO` também mantém o ID `Long` legado; ambos devem ser revistos antes de atender entidades UUID geradas no cliente.

Os pacotes permanecem abaixo de `br.ufla.citogenetica`, raiz de `@SpringBootApplication` e `@ConfigurationPropertiesScan`. A reorganização não exige mudar propriedades ou YAML. Migrations aplicadas nunca são editadas: novas tabelas entram em uma migration nova. Rotas, cookies, CSRF e schemas OpenAPI devem permanecer estáveis durante movimentações.

## Verificação e migração

ArchUnit, em escopo de teste, verifica acesso apenas a APIs públicas, ausência de dependências de `shared` para negócio/config, ausência de dependências de módulos para `config` e aciclicidade. Há também um teste de regressão do conjunto de paths em `/v3/api-docs`. O teste documenta os pacotes legados ainda tolerados (`controller`, `exception`, `filter`, `mapper`, `model`, `repository` e `service`); os PRs 3–4 removem os pacotes correspondentes da lista.

Migração aprovada em quatro PRs:

1. Documentação/ADR, ArchUnit, `exemplo` e `shared`.
2. `config` e adaptadores `infra` (segurança, sessão, OpenAPI, e-mail, segredos e storage).
3. Módulo `usuario`.
4. Módulo `notificacao`, destinatário por ID escalar sem alterar coluna/FK, evento de sessão no logout e endurecimento final das regras.
