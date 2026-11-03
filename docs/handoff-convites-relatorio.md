Implementado na branch `feat/convites-laboratorio`, sem stage nem commit. Não adicionei dependências nem alterei `TODO.md`.

### Arquivos por área

- **Convites:** entidade, estados, persistência, serviço, controles REST, token SHA-256, rate limit, expiração em lotes e e-mail após commit em `laboratorio.internal`. O aceite reutiliza um serviço próprio para criar ou reaproveitar a participação, que também poderá servir ao PR C.
- **Integração entre módulos:** a consulta pública mínima de usuário retorna apenas dados necessários ao convite; `notificacao` consome o evento público de laboratório após commit, em nova transação. Evento, `toString()` e logs não carregam o token.
- **Segurança e configuração:** rotas protegidas por sessão e CSRF; URL de convite validada e configurável. Atualizados [`.env.example`](</home/scarabeli/Área de trabalho/Faculdade/Biologia/Biologia/.env.example>), [application.yml](</home/scarabeli/Área de trabalho/Faculdade/Biologia/Biologia/apps/api/src/main/resources/application.yml>) e [docs/arquitetura.md](</home/scarabeli/Área de trabalho/Faculdade/Biologia/Biologia/docs/arquitetura.md>).
- **Banco e API:** nova [migration V10](</home/scarabeli/Área de trabalho/Faculdade/Biologia/Biologia/apps/api/src/main/resources/db/migration/V10__convites_laboratorio.sql>) e paths OpenAPI atualizados em [CitogeneticaApplicationTests.java](</home/scarabeli/Área de trabalho/Faculdade/Biologia/Biologia/apps/api/src/test/java/br/ufla/citogenetica/CitogeneticaApplicationTests.java>).

### Rotas

Criação exige papel **ADMIN local**; papel global `usuario.ADMIN` não concede acesso ao laboratório.

```http
POST /api/v1/laboratorios/{laboratorioId}/convites
Content-Type: application/json

{"email":"pesquisadora@exemplo.org","papeis":["MEMBRO","ORIENTADOR"]}
```

Retorna `201` com dados do convite, sem token. `GET` no mesmo caminho lista convites pendentes. `POST .../{conviteId}/reenviar` substitui o token e retorna `200`; `DELETE .../{conviteId}` revoga e retorna `204`.

```http
POST /api/v1/convites/aceitar
{"token":"<token recebido no link>"}
```

`POST /api/v1/convites/recusar` usa o mesmo corpo. Ambos exigem sessão, CSRF e e-mail confirmado igual ao destino; sucesso retorna `204`. Convite inválido, divergente ou indisponível retorna erro genérico. Membro sem papel recebe `403`; quem não participa recebe `404`; limites retornam `429` com `Retry-After`.

Convite para quem já é participante ativo retorna `409` claro, pois o roster já exibe os e-mails. Em laboratório desativado, envio, reenvio e aceite são bloqueados; mantive a revogação disponível ao admin local para invalidar acesso futuro.

### Migration e propriedades

A V10 cria `convite_laboratorio`, com estado, validade, hash do token, papéis concedidos e auditoria, além de `convite_laboratorio_rate_limit`. Inclui índice único parcial para convite pendente por laboratório/e-mail, hash único, constraints de estado e índices de expiração. A limpeza processa convites vencidos em lotes; também limpa janelas antigas de rate limit e não roda no perfil de comando administrativo.

As novas variáveis são `API_SECURITY_LABORATORY_INVITATIONS_EXPIRATION_DAYS`, `...CLEANUP_INTERVAL_MS`, `...CLEANUP_BATCH_SIZE`, `...RATE_LIMIT_WINDOW_MINUTES`, `...RATE_LIMIT_SEND_PER_LABORATORY_AUTHOR`, `...RATE_LIMIT_ACCEPT_IP`, `...RATE_LIMIT_ACCEPT_USER` e `API_FRONTEND_LABORATORY_INVITATION_URL`. O link usa `#token=` e a URL é validada; `.env.example` traz o endereço local de desenvolvimento. Nenhuma variável nova é segredo.

### Verificação

`./mvnw -Dmaven.repo.local=/tmp/citogenetica-m2 clean test`: **184 testes passaram**, incluindo **14 testes de integração de convites com Testcontainers**, ArchUnit e OpenAPI. `git diff --check` também passou.

Os testes cobrem cadastro e confirmação do destinatário, divergência de e-mail, tokens inválidos/expirados/reutilizados/substituídos/revogados, concorrência, reingresso, recusa, papéis concedidos, notificação, hash no banco, rate limit, limpeza e laboratório desativado.

### Riscos e PR C

O envio de e-mail usa fila assíncrona em memória após o commit; uma interrupção do processo pode perder um envio já agendado. O convite permanece salvo e pode ser reenviado pelo admin. Um outbox persistente pode ser considerado se essa garantia for necessária.

O PR C fica com convites de orientação, aceite que também adiciona o pesquisador ao laboratório quando necessário e regras/testes de coorientação. A imagem `apps/web/public/images/celulas-editorial.png` já estava não rastreada e permaneceu intacta.