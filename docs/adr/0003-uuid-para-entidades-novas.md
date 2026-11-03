# ADR 0003 — UUID e sincronização offline para entidades novas

- **Status:** aceita
- **Data:** 2026-11-03

## Contexto

O aplicativo deverá criar registros offline e sincronizá-los depois. As entidades existentes (`Usuario`, `Notificacao` e `Exemplo`) já usam IDs `Long` e não serão migradas. Registros criados offline precisam manter o mesmo identificador em todas as tentativas de sincronização; registros criados online podem receber o identificador do servidor.

## Decisão

Entidades novas que precisem dessa capacidade usarão UUIDv4 atribuído antes do `INSERT`, armazenado no tipo nativo `uuid` do PostgreSQL. O cliente poderá fornecer o ID para criações offline; criações online usarão a fábrica compartilhada do servidor. IDs não são credenciais e sua posse será sempre verificada pelos serviços de cada módulo.

O contrato de sincronização é:

- mesmo ID, mesmo dono e mesmo conteúdo representam repetição idempotente da operação;
- mesmo ID e mesmo dono com conteúdo diferente resultam em conflito;
- ID associado a outro dono deve ter a mesma resposta observável que um ID desconhecido, sem sobrescrever nem revelar o registro;
- atualizações offline exigem a versão esperada (`@Version`); versão obsoleta resulta em conflito.

Uma base de persistência separada fornece auditoria `Instant` em `created_at` e `last_modified_at`, controlada pelo servidor, e versão `Long` com `@Version`. A base atual `BaseEntity` e as entidades `Long` permanecem inalteradas. A validação de IDs recebidos aceita somente UUIDv4 canônico em minúsculas.

## Consequências

Não há migração de produção neste momento. Cada módulo criará suas próprias tabelas e aplicará o contrato de propriedade, idempotência e concorrência. Timestamps novos representam instantes UTC e não dependem do fuso horário do dispositivo.
