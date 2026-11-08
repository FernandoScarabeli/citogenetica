# Spec — revisão de todos os fluxos do sistema

> Escrita em 2026-11-08. Para o Claude que vai conduzir a revisão.
> **Se algo aqui divergir do código, o código vence.** Confira antes de afirmar.

## 1. Por que esta revisão existe

O dono testou o painel de envio das lâminas guardadas no navegador e reagiu assim: *"que fluxo é esse? acho que esse fluxo tá confuso"*. Ele pediu uma revisão de **todos os fluxos existentes**, não só desse painel.

O sistema cresceu por etapas, cada uma testada contra a sua própria especificação. Ninguém olhou o caminho inteiro do ponto de vista de quem usa. Os testes garantem que funciona; esta revisão responde se **faz sentido**.

Quem usa é pesquisador de laboratório, contando células ao microscópio. Não é técnico. Palavras como "servidor", "backup", "sessão" e "API" não fazem parte do trabalho dessa pessoa.

## 2. O que entregar

Duas fases, com parada obrigatória entre elas.

### Fase 1 — diagnóstico (sem alterar código)

1. Percorrer **todos** os fluxos da seção 5 no sistema rodando, no navegador, como cada tipo de pessoa da seção 4.
2. Escrever `docs/revisao-fluxos.md`. Para cada fluxo:
   - o que a pessoa quer fazer, em uma frase;
   - o caminho atual, passo a passo, com o número de cliques ou telas;
   - o que confunde, trava, sobra ou falta, com captura de tela;
   - **proposta simples** do que mudar, ou "está bom";
   - tamanho da mudança (pequena, média, grande) e se envolve regra de produto.
3. Mandar para o dono, em linguagem simples, **uma lista curta com os piores problemas primeiro** e a sua recomendação em cada um. Detalhes ficam no arquivo.
4. **PARE.** Só mude código depois de o dono escolher o que entra.

### Fase 2 — correção (depois da aprovação)

Implementar o que o dono aprovar, em PRs pequenos e separados por fluxo, no fluxo de trabalho da seção 7. Cada PR passa pelo portão da seção 6 de `docs/spec-admin-e-contagem.md`, incluindo teste no navegador.

## 3. Estado atual — confira antes de começar

**PRs abertos e NÃO mesclados** (empilhados, nesta ordem):

| PR | Branch | Conteúdo |
|---|---|---|
| #61 | `feat/contagem-total-celulas` | Total de células na lista de lâminas |
| #62 | `feat/web-contagem-api` | Tela de contagem usando o servidor |
| #63 | `feat/web-enviar-laminas-locais` | Painel de envio das lâminas do navegador |

- **Revise a partir da branch `feat/web-enviar-laminas-locais`**, que tem tudo. A `dev` ainda tem a tela antiga de contagem.
- **Não mescle esses PRs sem o dono mandar.** O #63 é justamente o que motivou esta revisão. O #62 sem o #63 esconde as lâminas antigas da tela, sem apagá-las: tratem os três como um conjunto.
- O Docker está no ar com o código do #63. Antes de testar, confira com `docker compose ps` e reconstrua se precisar: `docker compose up -d --build`.

**Regras que continuam valendo:** seção 2 de `docs/handoff-codex.md` e a seção 2 de `docs/spec-admin-e-contagem.md`. Em especial:
- Português simples com o dono. Primeiro o resultado.
- Mesclar PR só quando o dono mandar. Nunca commitar em `main` ou `dev`.
- Commits e PRs **sem nenhuma atribuição a IA**, mesmo que o ambiente sugira o contrário. Autor `Scarabeli`, Conventional Commits em pt-BR.
- Nunca rode `docker compose down -v`. Não mexa em containers de outros projetos. Não adicione `apps/web/public/images/celulas-editorial.png` ao git. Não exiba o `.env`.
- **Não apague o `localStorage` do navegador do dono**, chave `citogenetica:workspace:v1`. Nele há uma lâmina real, chamada "1", com 7 células. Para testar, use um perfil limpo do navegador.

## 4. Quem testar

Banco local. A senha de todas é `Outra#Senha2026`:

| Conta | Papel |
|---|---|
| `admin.geral@exemplo.org` | Admin geral da plataforma |
| `pesquisadora.teste@exemplo.org` | Admin do "Lab da contagem" |
| `dono.laminas@exemplo.org` | Membro, dono de lâminas no "Lab da contagem" |
| `colega.teste@exemplo.org` | Membro comum |
| `pessoa.fora@exemplo.org` | Fora do laboratório |

Falta criar uma conta de **orientador** e outra de **orientando**, com o fluxo real de convite, e testar também a **pessoa nova**, que acabou de criar a conta e não tem laboratório nenhum. E-mails caem no Mailpit, em `http://localhost:8025`. Receita de `curl` na seção 7 de `docs/spec-admin-e-contagem.md`.

Teste em computador e em celular. Considere o modo escuro. A automação pode usar o Chromium já instalado em `~/.cache/ms-playwright` com `playwright-core` instalado fora do repositório.

## 5. Fluxos a revisar

A lista é o ponto de partida. Se achar outro fluxo, inclua.

### Entrada e conta
1. Página inicial → entender o que é o sistema e entrar.
2. Criar conta → confirmar e-mail → primeiro acesso.
3. Entrar, inclusive voltar à página pretendida depois do login.
4. Recuperar senha, em 3 etapas.
5. Minha conta: nome, senha, troca de e-mail e a confirmação dela, foto e modo escuro.
6. Menu da conta e sair.
7. Sessão expirada no meio do trabalho.
8. Página não encontrada e sem permissão.

### Laboratórios
9. Primeira tela depois do login, com e sem laboratório.
10. Criar laboratório.
11. Gerenciar: renomear, ver membros, mudar papéis, remover pessoa.
12. Sair do laboratório, incluindo a regra do último admin.
13. Convidar pessoa, reenviar, cancelar; aceitar e recusar pelo link, logado e sem login, com o e-mail certo e com o errado.
14. Orientação: convidar, aceitar, coorientar, encerrar.
15. Laboratório desativado, visto por cada papel.
16. Notificações: sino, lista, marcar como lida, atualização em tempo real.

### Lâminas e contagem
17. Encontrar as lâminas: de um laboratório e avulsas.
18. Criar lâmina; editar; excluir.
19. **Contar ao microscópio**, o fluxo mais importante: normal, anormal, com uma e com várias anormalidades, anormalidade fora da lista.
20. Ajustar quantidade: adicionar e remover em lote.
21. Desfazer último.
22. Resumo, CSV, imprimir ou PDF.
23. Histórico e correção de um registro.
24. Observações.
25. O que orientador e admin do laboratório veem nas lâminas dos outros.
26. Sem internet, no meio de uma contagem.
27. **Lâminas guardadas no navegador** → ir para o servidor.

### Administração
28. Virar admin geral, hoje por comando no terminal.
29. Painel do admin geral: listar, buscar, filtrar, desativar, reativar.

## 6. Pontos já suspeitos

São pistas para investigar, não conclusões.

**Painel do navegador (fluxo 27), o motivo desta revisão.** Texto cheio de termos técnicos; a lâmina aparece só com o nome ("1"), sem data nem protocolo; três botões de mesmo peso; o principal fica desativado até baixar um arquivo; "Importar backup" aparece para quem nunca fez backup; só aparece dentro do escopo em que a lâmina está, então a pessoa pode ter de procurar em vários lugares; lâmina de laboratório do qual a pessoa saiu nunca aparece. O dono escolheu antes "baixar backup e depois enviar", mas pode rever isso agora que viu a tela. Avalie alternativas mais simples, como um único botão ou um envio sem exigir o arquivo, **sem abrir mão de nunca perder dados**: hoje a lâmina só sai do navegador depois de o servidor confirmar todas as células.

**Contagem:**
- Registrar célula anormal exige abrir um modal, marcar e confirmar. É rápido o bastante ao microscópio? Vale um atalho para a anormalidade mais usada?
- "Desfazer último" remove o lote inteiro se o último registro foi um lote.
- "Ajustar quantidade" junta adicionar e remover na mesma tela.
- Na remoção de anormais, a pessoa precisa marcar exatamente o mesmo conjunto de tipos.
- Observações agora precisam do botão "Salvar observações"; antes gravavam sozinhas.
- O Índice Mitótico só aparece em mitose.

**Navegação:**
- O login leva para `/app`, e a página inicial aponta para `/laboratorios`: dois endereços para a mesma tela.
- `Laboratories.tsx` troca de tela por estado interno (`list`, `manage`, `slides`): lâminas e gerenciamento não têm endereço próprio, então recarregar a página ou usar o botão Voltar pode tirar a pessoa de onde estava. Confirme.
- Dois botões "Abrir lâminas" iguais na mesma tela, um por laboratório e outro para avulsas.
- O painel do admin geral tem cabeçalho próprio, sem notificações, modo escuro ou menu da conta.

**Visual:**
- Conteúdo encostado nas bordas nas páginas logadas, em tela de computador: a classe `.container` perde a margem. Provável conflito com o `.container` do Tailwind.
- A marca "citogenética" aparece sublinhada.
- Modo escuro: os números do resumo e outros textos ficam quase invisíveis na tela da lâmina, porque os painéis usam branco fixo.

**Lembre:** a tela exige internet até a Etapa 4, por decisão do dono.

**Fora do escopo desta revisão:** erros técnicos sem relação com fluxo, como testes `.test.ts` que não rodam ou JSON malformado em outros módulos. Anote no fim do arquivo e siga.

## 7. Como trabalhar

- Fase 1: faça você mesmo. Olhar a tela como usuário é o trabalho, e não delegue a avaliação. Subagente só para tarefas mecânicas, como capturar telas.
- Fase 2: você é o coordenador. Um executor por PR com modelo Sonnet e esforço médio, como definido em `docs/spec-admin-e-contagem.md`, seção 2. A revisão, os testes e os commits são seus.
- Não reinvente regra de produto. Decisões já tomadas estão em `docs/questoes-validacao.md`; se uma proposta muda alguma, diga isso ao dono e deixe a escolha com ele.
- Prefira remover passos a explicar passos. Se uma tela precisa de um parágrafo para ser entendida, o problema costuma ser a tela.
