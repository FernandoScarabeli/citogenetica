# ADR 0002 — Bootstrap de uso único do primeiro administrador global

- **Status:** aceita
- **Data:** 2026-11-03

## Contexto

O papel `ADMIN` é global e fica acima dos papéis por laboratório, que serão definidos no módulo `laboratorio`. Não existe usuário semeado nem rota pública de promoção. O primeiro administrador precisa ser preparado operacionalmente sem abrir uma forma permanente de escalada de privilégio pela API.

## Decisão

Uma pessoa cria e confirma sua conta pelo fluxo normal. Quem opera o deploy executa um comando explícito da mesma aplicação Spring Boot, com tipo de aplicação `NONE`, sem agendadores e sem servidor HTTP. O comando recebe o e-mail como argumento, exige conta existente, ativa e confirmada, promove para `ADMIN`, revoga as sessões existentes e encerra com código de saída. Uma linha singleton em `bootstrap_admin_plataforma`, protegida por bloqueio no banco e atualização condicional, registra o único consumo em uma transação. O registro não tem chave estrangeira para a conta e permanece se ela for excluída. A auditoria da promoção registra somente o ID do usuário após o commit.

Se futuramente não houver administradores e o marcador já tiver sido consumido, a recuperação será manual por DBA, com a API parada, backup, verificação institucional de identidade, transação e revogação de sessões. O procedimento não redefine o marcador.

## Consequências

Não há promoção automática do primeiro cadastro, rota HTTP, segredo de bootstrap nem papel de laboratório atribuído. A operação exige acesso ao ambiente de deploy e ao banco. O procedimento excepcional é deliberadamente manual e deve ser registrado pela operação.
