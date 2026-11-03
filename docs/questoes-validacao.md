# Questões de validação

Decisões de produto e perguntas ao laboratório. Cada item tem status:
**decidido** (com data e quem decidiu) ou **pendente** (com a etapa em que a resposta passa a ser necessária).

Quando uma resposta mudar, atualize o item e a data, não apague o histórico da decisão.

## Contas e cadastro

| Questão | Status | Decisão |
|---|---|---|
| Quem pode criar conta? | decidido (2026-11-03, mantenedor) | Qualquer e-mail, sem lista de domínios. |
| A conta precisa de aprovação manual? | decidido (2026-11-03, mantenedor) | Não. Confirmar o e-mail basta para entrar; o acesso aos dados de um laboratório vem por convite. |
| Tamanho mínimo da senha | decidido (2026-11-03, mantenedor) | 8 caracteres, máximo 64, sem regras de composição; senhas comuns ou vazadas são recusadas. |
| Campos do cadastro | decidido (2026-11-03, mantenedor) | Nome, e-mail e senha. Instituição e vínculo ficam no laboratório, não no perfil. |
| Existe um administrador da plataforma? | decidido (2026-11-02, mantenedor) | Sim, acima dos laboratórios, para parâmetros do sistema e, no futuro, um painel. É separado dos papéis de cada laboratório. |

## Laboratórios, participação e convites (Etapa 1)

| Questão | Status | Decisão |
|---|---|---|
| Quem cria um laboratório? | decidido (2026-11-03, mantenedor) | Qualquer usuário com e-mail confirmado. Quem cria vira administrador do laboratório. O administrador da plataforma pode desativar laboratórios abusivos. |
| Quantos administradores um laboratório pode ter? | decidido (2026-11-03, mantenedor) | Vários, nunca zero: o último administrador não pode sair nem ser rebaixado sem deixar outro no lugar. |
| Quem convida pessoas para um laboratório? | decidido (2026-11-03, mantenedor) | Administradores do laboratório e orientadores. |
| Quem cria o vínculo orientador ↔ pesquisador? | decidido (2026-11-03, mantenedor) | O orientador convida e o pesquisador aceita. |
| Um pesquisador pode ter mais de um orientador? | decidido (2026-11-03, mantenedor) | Sim (orientação e coorientação). |
| Quem vê as lâminas e contagens de um pesquisador? | decidido (2026-11-03, mantenedor) | O próprio pesquisador, seus orientadores e os administradores do laboratório. Os demais membros não veem. |
| O que acontece com os dados de quem sai do laboratório? | decidido (2026-11-03, mantenedor) | Ficam no laboratório, somente leitura. A pessoa perde o acesso. |

## Contagem (Etapa 2)

| Questão | Status | Decisão |
|---|---|---|
| Uma célula pode ter mais de uma anormalidade? | decidido (2026-11-03, mantenedor) | Sim. |
| Limites do lote, meta configurável, casas decimais | pendente (Etapa 2) | Perguntar ao laboratório. |
| Alcance do "desfazer" entre aparelhos | pendente (Etapa 2/4) | Perguntar ao laboratório. |
| Quais eventos geram notificação (convite, comentário, conflito...) | pendente (Etapa 1 em diante) | Definir junto com cada etapa. |

## Dados, exportação e operação

| Questão | Status | Decisão |
|---|---|---|
| Backups reais (ou fictícios) dos HTMLs antigos para testar a importação | pendente (Etapa 3) | Pedir ao laboratório. |
| Formato de CSV e PDF esperado (Excel, R, SISVAR) | pendente (Etapa 3) | Perguntar ao laboratório. |
| Hospedagem e responsável pela manutenção | pendente (antes da produção) | UFLA/DGTI ou serviço externo. |
| Termo de uso, aviso de privacidade e base legal (LGPD) | pendente (antes da produção) | Definir antes de abrir o cadastro em produção. |
