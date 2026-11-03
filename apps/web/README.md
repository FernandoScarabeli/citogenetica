# Citogenética — frontend

Aplicação de registro de lâminas de meiose e mitose para o Laboratório de Citogenética da UFLA.

## Executar

Requisitos: Node.js 26 LTS e npm. A versão principal do Node está em `.nvmrc`.

```sh
npm ci
npm run dev
```

Abra o endereço indicado pelo Vite. Para verificar os cálculos e gerar a versão de produção:

```sh
npm test
npm run build
npm run preview
```

### Docker Compose

Na raiz do repositório, gere o `.env` e inicie os serviços, incluindo a API, o frontend servido pelo Nginx e o Mailpit:

```sh
./scripts/gerar-env.sh
docker compose up -d --build
```

Se o `.env` já existe, use `./scripts/gerar-env.sh --add-missing` para acrescentar somente as variáveis que faltam. O comando preserva os valores existentes; atualize `API_FRONTEND_URL`, as variáveis `API_FRONTEND_*_URL` e `API_CORS_ALLOWED_ORIGINS` para acompanhar `WEB_PORT`. O site fica em <http://localhost:3000> por padrão; a porta pode ser alterada com `WEB_PORT`. Os e-mails enviados no ambiente local aparecem no Mailpit em <http://localhost:8025> (porta configurável por `MAILPIT_WEB_PORT`). As URLs de confirmação e convite do template apontam para o site servido pelo Compose.

### Desenvolvimento com a API

O front usa caminhos relativos em `/api` e o Vite os encaminha para `http://localhost:8080`, incluindo conexões SSE sem buffering. Suba a API e seus serviços locais conforme `../api/README.md`; em outro terminal:

```sh
cd apps/web
npm run dev
```

Para apontar o proxy a outro host, defina `API_PROXY_TARGET` no ambiente ao iniciar o Vite (ex.: `API_PROXY_TARGET=http://localhost:9080 npm run dev`). O padrão é `http://localhost:8080`. A variável é lida só pelo servidor de desenvolvimento e não vai para o navegador. Cookies de sessão e CSRF seguem na mesma origem do Vite por meio do proxy.

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

As lâminas e contagens são armazenadas no `localStorage` deste navegador, sob `citogenetica:workspace:v1`. Use **Backup** para guardar uma cópia e **Importar** para restaurá-la; a aplicação apresenta quantas lâminas serão substituídas antes de confirmar. A importação atual aceita os backups JSON gerados por esta aplicação, não os arquivos das ferramentas anteriores.

O front autentica por sessão da API: verifica `/api/v1/usuarios/me` ao abrir, usa `/api/v1/auth/login` e `/api/v1/auth/logout`, e envia o token CSRF pelas rotas protegidas. `/`, `/entrar` e as rotas de pesquisa usam navegação declarativa; laboratórios, perfil e contagem exigem uma sessão válida.

Essa sessão ainda não isola os registros de pesquisa. Lâminas e contagens continuam armazenadas localmente no navegador e não são sincronizadas nem associadas à conta autenticada. O navegador pode apagar esses dados; mantenha backups. Os protocolos e cálculos estão em `src/domain.ts` e o acesso a lâminas em `src/storage.ts`.

O relatório usa a impressão do navegador: na aba **Resumo**, escolha **Imprimir / PDF** e, na janela de impressão, selecione salvar em PDF.

## Laboratórios e participações

Após entrar, `/laboratorios` consulta os laboratórios da conta em `/api/v1/laboratorios`. A criação envia somente o nome; a API atribui Membro e Administrador a quem cria. O nome pode ser alterado por Administradores. Participantes ativos veem nome, e-mail e papéis. Os papéis Membro, Administrador e Orientador são combináveis; Membro é obrigatório. A API é a fonte de autorização e de conflitos, incluindo a proteção do último Administrador. Um laboratório desativado fica disponível apenas para consulta, inclusive nas lâminas locais.

A configuração local antiga `citogenetica:laboratories:v1` é removida silenciosamente na inicialização. A limpeza afeta somente essa chave; `citogenetica:workspace:v1` e `citogenetica:preferences:v1` permanecem intactas. Convites e orientação são geridos pela API e o cabeçalho autenticado mostra as notificações da conta.

As lâminas novas em um laboratório real recebem o UUID dele e aparecem somente naquele espaço. Lâminas sem `labId` e lâminas com identificadores locais antigos que não são UUID continuam acessíveis em **Lâminas avulsas**. As lâminas e contagens continuam locais e não são transferidas nem apagadas ao criar ou sair de um laboratório. O backup aberto em um laboratório contém somente as lâminas daquele UUID; em **Lâminas avulsas**, inclui os registros sem vínculo real.

### Metas e ajustes de contagem

A meta é opcional e pode ser definida ao criar ou editar a lâmina. Lâminas de mitose anteriores a esse campo continuam mostrando 1.000; lâminas anteriores de meiose continuam sem meta. Backups anteriores permanecem compatíveis, e os registros e datas existentes são preservados. O CSV inclui as observações ao final.

A ação de quantidade adiciona ou remove células por fase e classificação; para anormais, o tipo é obrigatório. A remoção seleciona os registros mais recentes da classificação e não permite remover mais do que existe. Desfazer restaura a última alteração de contagem feita na sessão, inclusive um lote; após recarregar, o desfazer volta a remover apenas o último registro. A confirmação de regras científicas e do uso de contagem em lote com o laboratório continua necessária.

O nome e o e-mail exibidos no perfil vêm da conta autenticada. O nome é atualizado em /api/v1/usuarios/me; a senha é atualizada em /api/v1/usuarios/me/senha, mantendo a sessão atual e encerrando as demais. Foto e aparência continuam salvas apenas neste navegador. O campo name de versões antigas das preferências é ignorado, sem impedir a leitura da foto e do tema.

A troca de e-mail no front segue o contrato registrado em docs/handoff-coordenacao.md (POST /api/v1/usuarios/me/email e POST /api/v1/auth/confirmar-troca-email). Os controllers presentes neste checkout ainda não expõem essas rotas; até a implementação correspondente na API, as duas chamadas retornarão erro. A API também não oferece gerenciamento de dispositivos, por isso o perfil não mostra essa aba.

## Notificações

O sino consulta a contagem de não lidas e a lista paginada na API. Enquanto a sessão estiver ativa, cada aba mantém uma única conexão SSE autenticada pelo cookie; os eventos invalidam os dados locais e, ao abrir o painel ou retornar à aba, a contagem também é consultada novamente. Convites de laboratório e de orientação não incluem token na notificação; a pessoa deve usar o link recebido por e-mail.

O `EventSource` nativo não permite ler o status HTTP nem o cabeçalho `Retry-After` em uma falha. Quando o servidor encerra a conexão, o cliente verifica a sessão pela API antes de agendar nova tentativa. A falha por limite de conexões usa a espera de 30 segundos definida pela API; as falhas recuperáveis usam espera exponencial com teto de 30 segundos.
