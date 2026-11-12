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
| Uma pessoa pode ter mais de um papel no laboratório? | decidido (2026-11-03, mantenedor) | Sim. Os papéis (membro, administrador, orientador) se combinam. |
| Quem concede os papéis de administrador e orientador? | decidido (2026-11-03, mantenedor) | Somente administradores do laboratório. |
| O que acontece quando um laboratório é desativado? | decidido (2026-11-03, mantenedor) | Ninguém altera nada nem envia convites; os dados ficam e os membros continuam vendo. |
| Quem vê a lista de membros? | decidido (2026-11-03, mantenedor) | Todos os membros ativos veem nome, e-mail e papel dos colegas. |
| Quanto tempo vale um convite? | decidido (2026-11-03, mantenedor) | 7 dias. Reenviar invalida o link anterior. |
| Pode haver laboratórios com o mesmo nome? | decidido (2026-11-03, mantenedor) | Sim. |
| Quem encerra um vínculo de orientação? | decidido (2026-11-03, mantenedor) | O orientador ou um administrador do laboratório (o pesquisador não). |
| Quem saiu ou foi removido pode voltar? | decidido (2026-11-03, mantenedor) | Sim, somente com um convite novo. |
| Como o orientador convida? | decidido (2026-11-03, mantenedor) | Convida para orientação; se a pessoa ainda não for membro, o mesmo convite a coloca no laboratório. |

## Contagem (Etapa 2)

| Questão | Status | Decisão |
|---|---|---|
| Uma célula pode ter mais de uma anormalidade? | decidido (2026-11-03, mantenedor) | Sim. |
| O que pode ser criado sem internet? | decidido (2026-11-03, mantenedor) | Somente lâminas e contagens. Laboratórios e convites exigem conexão. |
| Guardar a hora do aparelho? | decidido (2026-11-03, mantenedor) | Sim, como dado separado e apenas informativo; a hora oficial é a do servidor. |
| Lâmina sem laboratório existe no servidor? | decidido (2026-11-08, mantenedor) | Sim, visível e editável só pelo dono. |
| Limite de células por registro em lote | decidido (2026-11-08, mantenedor) | 1000 por registro. O valor fica guardado no banco, pois o administrador da plataforma poderá alterá-lo numa futura tela de parâmetros do sistema. |
| Meta da lâmina | decidido (2026-11-08, mantenedor) | Opcional e por lâmina; número inteiro positivo. |
| Casas decimais nos percentuais | decidido (2026-11-08, mantenedor) | A API guarda as contagens e devolve o número sem arredondar. A tela mostra 2 casas. |
| Alcance do "desfazer" entre aparelhos | decidido (2026-11-08, mantenedor) | Desfaz os últimos registros da pessoa naquela lâmina, em qualquer aparelho. |
| A contagem gera notificação? | decidido (2026-11-08, mantenedor) | Não nesta etapa. |
| A tela de contagem funciona sem internet antes da Etapa 4? | decidido (2026-11-08, mantenedor) | Não. Ao ligar a tela ao servidor, lâminas novas e contagens exigem conexão; sem ela, a tela avisa e nada é contado. |
| Data original das contagens enviadas do navegador | decidido (2026-11-08, mantenedor) | Fica guardada como hora do aparelho e o histórico a mostra. A hora oficial continua sendo a do servidor. |
| Cópia local depois do envio ao servidor | decidido (2026-11-08, mantenedor) | A pessoa baixa um backup antes de enviar; com o envio confirmado, a lâmina sai do navegador. |
| Quais eventos geram notificação (convite, comentário, conflito...) | pendente (Etapa 1 em diante) | Convites já geram. Definir os demais junto com cada etapa. |

## Dados, exportação e operação

| Questão | Status | Decisão |
|---|---|---|
| Backups reais (ou fictícios) dos HTMLs antigos para testar a importação | decidido (2026-11-12, mantenedor) | Não há backups reais: os exemplos de teste são gerados usando os próprios HTMLs. |
| Formato de CSV e PDF esperado (Excel, R, SISVAR) | decidido (2026-11-12, mantenedor) | CSV no padrão do Excel em português: separador `;`, vírgula decimal, UTF-8 com BOM (o R lê com `read.csv2`; o SISVAR importa pelo Excel). PDF gerado pela API com OpenPDF. |
| Exportar várias lâminas de uma vez | decidido (2026-11-12, mantenedor) | Sim, em JSON e nos dois CSVs (resumo e uma linha por registro). |
| Destino das lâminas importadas dos HTMLs antigos | decidido (2026-11-12, mantenedor) | A pessoa escolhe um laboratório (ou avulsa) uma vez, e vale para o arquivo inteiro. Importar de novo não duplica células. |
| Hospedagem e responsável pela manutenção | pendente (antes da produção) | UFLA/DGTI ou serviço externo. |
| Termo de uso, aviso de privacidade e base legal (LGPD) | pendente (antes da produção) | Definir antes de abrir o cadastro em produção. |
