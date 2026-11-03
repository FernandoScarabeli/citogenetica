---
name: Citogenética — Laboratórios
description: Identidade do sistema de Citogenética; extensão Operate.
colors:
  lab-accent: "#263d32"
  lab-ink: "#212924"
  lab-muted: "#59675e"
  lab-line: "#e3e8e3"
  lab-surface: "#fff"
  lab-ground: "#edf1ed"
  background: "#f5f6f4"
  focus: "#85a49b"
typography:
  display:
    fontFamily: "'Inter Variable', sans-serif"
    fontSize: "28px"
    lineHeight: "34px"
  title:
    fontSize: "16px"
    lineHeight: "22px"
  subtitle:
    fontSize: "14px"
    lineHeight: "20px"
  body:
    fontSize: "13px"
    lineHeight: "20px"
  caption:
    fontSize: "12px"
    lineHeight: "16px"
  micro:
    fontSize: "11px"
    lineHeight: "14px"
rounded:
  field-tag: "4px"
  button: "5px"
  panel: "6px"
spacing:
  control-gap: "8px"
  panel-inset: "24px"
  columns: "32px"
components:
  button-primary:
    backgroundColor: "{colors.lab-accent}"
    textColor: "#fff"
    rounded: "{rounded.button}"
    padding: "9px 15px"
---

# Design System: Citogenética — Laboratórios

## Overview

Extensão Operate sobre a identidade existente: Inter Variable local, verde florestal, superfícies brancas e fundo sálvia. Listas, bordas discretas e controles compactos mantêm continuidade com as lâminas.

Registro da implementação aprovada **SHIP**, sem correções obrigatórias. Direção herdada em `.impeccable/laboratorios.md`; crítica e capturas finais em `.impeccable/review/`. Não há `PRODUCT.md`; este registro não define decisões de produto.

## Colors

Verde nas ações, tinta e texto secundário na leitura, branco e sálvia nos planos, linhas claras nas divisões. Tokens locais de Laboratórios acima; os tokens globais de prosa em `theme.css` têm valores próprios e não são substituídos por este registro.

## Typography

Inter Variable local com eixo óptico. O frontmatter registra a escala global. Na extensão, parágrafos usam 14px/1.65, títulos de seção 16px/1.4 e nomes de laboratório 19px/1.4. A classe utilitária de display aplicada ao título da lista prevalece na cascata. O README documenta as seis funções e suas variantes compactas.

## Layout

Quadro central até 1200px, margens laterais de 32px; até 800px, 20px; até 600px, 16px. Lista e convites: coluna lateral de 316px e intervalo de 32px; até 1000px, 285px e 24px. Até 700px, uma coluna, com laboratórios antes dos convites. Gestão segue listas de membros e formulários; configurações empilham nesse mesmo limite.

## Elevation & Depth

Painéis de Laboratórios planos, com bordas e fundos. Sombras ficam nos diálogos herdados de lâminas, sem promover elevação aos painéis.

## Shapes

Cantos discretos nos controles e painéis, conforme os raios extraídos. Iniciais de conta e membros circulares.

## Components

Ações primárias verdes; secundárias brancas com borda. Papéis e estados aparecem em etiquetas textuais; abas têm sublinhado na seleção. Em Laboratórios, foco visível usa contorno de 3px, afastado 3px. Botões têm altura mínima de 40px, passando a 44px até 700px. Transições de cor de 160ms e deslocamento de 1px ao pressionar respeitam movimento reduzido.

Conta, laboratórios, membros, papéis, convites e orientação usam a API real. Os registros de pesquisa (lâminas e contagens) ainda ficam no navegador e não são isolados por conta, até a etapa de contagem da API.

## Do's and Don'ts

- Preservar Inter, verde/sálvia, margens e cabeçalho herdados ao estender telas.
- Preservar foco visível, linguagem clara sobre estados locais e ordem linear no celular.
- Não apresentar lâminas e contagens locais como sincronizadas com o servidor.
- Não converter pendências de validação em decisões de produto confirmadas.

Fontes: `apps/web/src/laboratories.css`, `apps/web/src/styles.css`, `apps/web/src/theme.css`, `apps/web/src/typography.css`, `apps/web/src/Laboratories.tsx`, `apps/web/README.md`, `.impeccable/laboratorios.md` e `.impeccable/review/critique.md`.

## Gestão compacta do laboratório

A gestão usa uma coluna de até 1100px e uma superfície única para as quatro abas,
com borda discreta e sem sombra. Nome e estado ficam juntos no cabeçalho. Na lista
de membros, o e-mail acompanha o nome; os papéis e ações mantêm colunas próprias.
Formulários de nome e convite têm largura máxima de 600px, com ações próximas aos
campos. A saída do laboratório aparece somente em Configurações.

Os ajustes ficam em `apps/web/src/laboratory-management.css`, limitados à gestão,
e usam os tokens existentes, inclusive no modo escuro. No celular, as abas rolam
horizontalmente e membros e formulários passam a uma coluna.

## Avisos de sucesso

Confirmações de criação e alteração de laboratório, papéis, convites e orientação
usam um aviso flutuante no canto superior direito, sem alterar o fluxo da página.
O aviso fecha em cinco segundos, oferece fechamento manual e pausa enquanto há
mouse ou foco sobre ele ou enquanto a página está oculta. Usa Inter, verde escuro
e ícones Lucide. Erros que exigem correção continuam junto do respectivo fluxo.

Componentes reutilizáveis em `apps/web/src/components/ui/alert-1.tsx` e
`floating-notice.tsx`, adaptados à estrutura React/TypeScript e ao tema existente,
sem novas dependências.

O aviso entra em 220ms e sai em 180ms, com opacidade e deslocamento vertical de
12px, usando transições que podem ser interrompidas por uma nova mensagem.
O conteúdo permanece montado durante a saída. Com movimento reduzido, somente
a opacidade muda.

## Movimento entre abas

As abas de gestão usam os tokens de Fluid Functionalism em `src/lib/springs.ts`,
com entrada `moderate` de 160ms e saída de 120ms, sem quique. O conteúdo combina
fade com deslocamento curto. Abas em saída ficam inertes e fora da árvore de
acessibilidade. A preferência por movimento reduzido mantém somente o fade.
Motion carrega seus recursos de animação em um arquivo separado, sob demanda.
