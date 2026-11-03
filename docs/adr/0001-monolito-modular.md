# ADR 0001 — Monólito modular leve

- **Status:** aceita
- **Data:** 2026-11-02

## Contexto

A API Spring Boot está organizada principalmente por camadas técnicas, enquanto o domínio prevê vários assuntos com regras próprias. Queremos fronteiras verificáveis sem dividir o deploy nem introduzir complexidade de microsserviços.

## Decisão

Organizar por módulos de domínio em português, com camadas dentro de cada módulo. Cada módulo expõe apenas contratos em `api`; detalhes ficam em `internal`. Módulos não acessam internos alheios. `shared` não depende de módulos, `config` ou `infra`; módulos de negócio usam portas de `shared`, e `infra` não depende de negócio. `config` compõe a aplicação por APIs públicas e não é dependência de módulos. O grafo não pode ter ciclos. Usar ArchUnit apenas em testes.

O administrador global permanece separado dos papéis por laboratório. As migrations já aplicadas permanecem imutáveis.

## Consequências

O monólito continua com um deploy e banco compartilhado, mas integrações passam por APIs ou eventos públicos. O `Exemplo` é o CRUD vertical de referência. A notificação guarda o ID escalar do destinatário, preservando a coluna e a FK existentes; a existência é consultada pela API pública de `usuario`. O logout publica um evento de sessão consumido por `notificacao`, sem dependência da configuração de segurança para SSE. Rotas, configuração e contratos HTTP permanecem estáveis.
