# ADR 0001 — Monólito modular leve

- **Status:** aceita
- **Data:** 2026-11-02

## Contexto

A API Spring Boot está organizada principalmente por camadas técnicas, enquanto o domínio prevê vários assuntos com regras próprias. Queremos fronteiras verificáveis sem dividir o deploy nem introduzir complexidade de microsserviços.

## Decisão

Organizar por módulos de domínio em português, com camadas dentro de cada módulo. Cada módulo expõe apenas contratos em `api`; detalhes ficam em `internal`. Módulos não acessam internos alheios, `shared` não depende de negócio/config e o grafo não pode ter ciclos. A raiz `config` compõe a aplicação e não é dependência de módulos. Usar ArchUnit apenas em testes.

O administrador global permanece separado dos papéis por laboratório. As migrations já aplicadas permanecem imutáveis.

## Consequências

O monólito continua com um deploy e banco compartilhado, mas integrações passam por APIs ou eventos públicos. O `Exemplo` será o CRUD vertical de referência. A migração ocorrerá em quatro PRs pequenos, mantendo rotas, configuração e contratos HTTP.
