# Citogenética — frontend

Aplicação de registro de lâminas de meiose e mitose para o Laboratório de Citogenética da UFLA.

## Executar

Requisitos: Node.js 22 e npm.

```sh
npm install
npm run dev
```

Abra o endereço indicado pelo Vite. Para verificar os cálculos e gerar a versão de produção:

```sh
npm test
npm run build
npm run preview
```

## Tipografia

O sistema [Fluid Functionalism](https://www.fluidfunctionalism.com/docs/typography) está instalado via registro shadcn, com Tailwind v4, alias `@/` e Inter Variable (incluindo o eixo óptico) servido pelo próprio app. As seis funções tipográficas são `display`, `title`, `subtitle`, `body`, `caption` e `micro`, com os tamanhos `default` e `compact`.

```tsx
import { typeClass } from '@/lib/type-scale';
import { SizeProvider, useSize, useTypeScale } from '@/lib/size-context';

<p className={typeClass('caption')}>Última atualização há 4 minutos</p>
<p className={typeClass('caption', 'compact')}>Em uma barra compacta</p>

// Dentro de um componente, acompanha o SizeProvider mais próximo:
const { type } = useSize();
const sizes = useTypeScale(); // Tamanhos em px para gráficos e outras integrações.
// <p className={type.body}>...</p>
// <SizeProvider size="compact">...</SizeProvider>
```

O app tem um `SizeProvider` na raiz. Para conteúdo rico, use `className="typeset"`, ou `"typeset typeset-compact"`; `not-typeset` exclui uma subárvore e `typeset-scroll` contém tabelas largas. Prefira `typeClass()` ao combinar tipografia com classes de cor em `cn()`/tailwind-merge.

Os tokens e estilos de prosa ficam em `src/theme.css`; `src/typography.css` adapta a aplicação sem modificar os arquivos de biblioteca em `src/lib`. Os estilos existentes usam `@layer components`, permitindo que as classes retornadas por `typeClass()` tenham prioridade. O reset Preflight não é carregado para preservar os estilos nativos usados pelo app.

Para atualizar a biblioteca:

```sh
npx shadcn@latest add https://www.fluidfunctionalism.com/r/typography.json --overwrite --yes
```

## Dados e integração

Os registros são armazenados no `localStorage` do navegador, sob uma chave versionada. Use **Backup** para guardar uma cópia e **Importar** para restaurá-la; a aplicação apresenta quantas lâminas serão substituídas antes de confirmar. A importação atual aceita os backups JSON gerados por esta aplicação, não os arquivos das ferramentas anteriores.

Não há autenticação, isolamento entre usuários, servidor, sincronização entre dispositivos ou garantia de recuperação dos dados locais. O navegador pode apagar esses dados; mantenha backups. Os protocolos e cálculos estão em `src/domain.ts` e o acesso ao armazenamento em `src/storage.ts`, para facilitar a futura integração com a API.

O relatório usa a impressão do navegador: na aba **Resumo**, escolha **Imprimir / PDF** e, na janela de impressão, selecione salvar em PDF.

## Fluxo de laboratórios do protótipo

O acesso local em `/` abre `/laboratorios` (também disponível em `/app`). A conta demonstrativa `pesquisador@exemplo.com` permite explorar criação e alternância de laboratórios, aceite/recusa de convite recebido, membros, atribuição de papéis hierárquicos, criação/revogação de convites, edição do laboratório e saída. A hierarquia local é Administrador → Orientador → Pesquisador: cada nível inclui as capacidades disponíveis dos níveis abaixo. O último administrador não pode sair nem perder seu papel sem outro administrador ativo. Registros antigos com múltiplos papéis são preservados e interpretados pelo nível mais alto. A orientação e o acesso coletivo ainda não estão implementados; as permissões precisam ser garantidas também no backend.

Os grupos Citogenética e Genética Vegetal, os membros Marina Alves, Rafael Lima e Clara Costa e seus e-mails são exemplos. Os convites são simulações locais: não enviam e-mail, não autenticam contas e não têm expiração real. Autorização, validade de convites e regras de concessão de administração precisam ser implementadas no backend. Vínculos de orientação continuam pendentes de validação, conforme a documentação.

As lâminas novas recebem `labId`; os registros antigos sem esse campo continuam em **Lâminas avulsas**. Alternar grupos não transfere lâminas. O backup de pesquisa contém somente as lâminas do espaço aberto; a importação pede confirmação e vincula os registros ao espaço escolhido, bloqueando colisões de IDs com outro espaço. A chave `citogenetica:laboratories:v1` mantém laboratórios, participações e convites separadamente dos dados de pesquisa. O backup de lâminas não inclui essa configuração demonstrativa. Sair ou remover um membro preserva os dados locais de pesquisa.

### Metas e ajustes de contagem

A meta é opcional e pode ser definida ao criar ou editar a lâmina. Lâminas de mitose anteriores a esse campo continuam mostrando 1.000; lâminas anteriores de meiose continuam sem meta. Backups anteriores permanecem compatíveis, e os registros e datas existentes são preservados. O CSV inclui as observações ao final.

A ação de quantidade adiciona ou remove células por fase e classificação; para anormais, o tipo é obrigatório. A remoção seleciona os registros mais recentes da classificação e não permite remover mais do que existe. Desfazer restaura a última alteração de contagem feita na sessão, inclusive um lote; após recarregar, o desfazer volta a remover apenas o último registro. A confirmação de regras científicas e do uso de contagem em lote com o laboratório continua necessária.

O nome de exibição, a foto de perfil e a aparência são preferências salvas apenas neste navegador; não alteram os dados de membros dos laboratórios nem constituem uma conta autenticada.

A gestão de dispositivos conectados e a alteração de senha dependem de autenticação e de uma API de sessões, ainda não implementadas.
