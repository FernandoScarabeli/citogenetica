# Orientações para o frontend

Frontend do sistema de Citogenética, em construção para integração com o backend. É um produto real, não uma peça descartável. Use português do Brasil e linguagem clara, própria do trabalho de pesquisa.

## 1. Comandos

Rode sempre dentro de `apps/web/`. Os demais scripts (desenvolvimento, lint) estão no `package.json`.

| Tarefa | Comando |
| --- | --- |
| Testes | `npm test` |
| Build de produção | `npm run build` |

## 2. Contexto do projeto

- Stack: React, TypeScript, Vite, Tailwind v4, CSS existente e `lucide-react`. Antes de adicionar biblioteca ou mudar a stack, verifique o `package.json` e os padrões atuais.
- Design: reaproveite os tokens, o `typeClass` e o `SizeProvider` existentes. Identidade: Inter Variable, verde e superfícies neutras. Referência: `../../DESIGN.md`.
- Não edite à mão os trechos gerados da biblioteca tipográfica.
- Armazenamento local: o projeto usa chaves versionadas para lâminas e laboratórios. Localize-as no código antes de mexer em persistência.
- Antes de definir contratos ou regras novas, consulte o backend e a documentação pertinentes. Respeite a modelagem e as decisões de produto existentes; não invente entidades.

## 3. Regras invioláveis

**Proibido:** rótulos, avisos, banners, comentários visíveis ao usuário ou nomes de componentes e arquivos que tratem o sistema como "protótipo", "demo", "mock", "dados fictícios" ou equivalentes. Dados de exemplo podem existir apenas dentro do adaptador local substituível e devem se comportar como registros normais do sistema.

### Honestidade da interface

- Não prometa capacidade que não existe. Cada ação exibida deve ter comportamento real no contexto atual; controle sem função não aparece como recurso disponível.
- Quando uma limitação afeta decisão ou segurança dos dados (sem sincronização, autenticação efetiva ou envio de convite), diga o comportamento concreto de forma discreta e clara. Não exponha detalhes de implementação sem necessidade.
- Totais e indicadores são derivados dos registros. Não invente métricas nem alegações de uso.
- Documente limitações técnicas no README.

### Dados

- Preserve dados existentes. Mudou o formato? Implemente validação e migração, trate falhas de leitura e escrita e faça backup.
- Nunca sobrescreva em silêncio dados que não puderam ser lidos.
- Dados de exemplo nunca são apresentados como dados reais de usuários.

### Segurança e acesso

- Papéis, permissões e convites são garantidos no backend. Verificação no frontend serve só à experiência.
- Regra de acesso ainda pendente de validação não vira decisão definitiva no código.

### Camada de dados

- Componentes não acessam `localStorage` nem detalhes de transporte diretamente. Tudo passa por uma camada de acesso a dados com contratos bem delimitados.
- Enquanto a API não existe, um adaptador local com dados de exemplo e persistência em `localStorage` é aceitável. Ele deve ser substituível pela API e não pode ser tratado como autenticação, autorização, e-mail ou sincronização reais.

## 4. Design

- Para criar, alterar ou revisar interfaces, use a skill `design-taste-frontend`. Para telas de trabalho, siga também `references/app-screens.md` da skill; para páginas de apresentação, siga o fluxo de página. Se a skill não estiver disponível no ambiente, siga `../../DESIGN.md` e os estilos existentes.
- Entenda o contexto e os fluxos antes de mudar a interface. Preserve navegação, dados e regras de negócio, salvo pedido explícito.
- `../../DESIGN.md` e o código atual são referência de identidade, não de texto. Se encontrar rótulos que contrariem estas orientações (por exemplo, "protótipo"), corrija-os no escopo que você está alterando e sinalize os demais ao usuário em vez de mexer fora do escopo.
- Priorize o trabalho cotidiano: hierarquia clara, densidade útil, tabelas e formulários legíveis, sem ornamento genérico.
- Responsivo em desktop, tablet e celular.
- Acessibilidade: HTML semântico, nomes acessíveis, teclado, foco visível, contraste, áreas de toque confortáveis, movimento reduzido.
- Contemple estados vazio, carregamento, erro e sucesso quando relevantes.

## 5. Código

- Componentes com responsabilidade clara; extraia componente reutilizável só diante de necessidade real.
- Tipagem explícita nas fronteiras (contratos, props públicas, acesso a dados).
- Estado na menor área necessária. Regras de domínio separadas da apresentação.

## 6. Definição de pronto

1. `npm test` e `npm run build` passam quando a alteração afeta código ou comportamento.
2. Alterações em cálculos, persistência, migrações e regras de acesso têm testes proporcionais ao risco.
3. Alterações visuais ou de interação foram verificadas na aplicação rodando, em desktop, tablet e celular, com teclado e estados relevantes. Siga a inspeção da skill na versão de produção quando aplicável.
4. Tudo que não foi possível verificar está informado com precisão na resposta final.
