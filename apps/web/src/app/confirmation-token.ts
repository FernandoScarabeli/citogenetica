let preparedToken: string | null | undefined;
let preparedUrl: string | undefined;
const tokenPaths = new Set(['/confirmar-email', '/confirmar-troca-email', '/convites/aceitar']);

function readAndRemoveToken(): string | null {
  const token = new URLSearchParams(window.location.hash.slice(1)).get('token');
  window.history.replaceState(
    window.history.state,
    '',
    `${window.location.pathname}${window.location.search}`,
  );
  return token;
}

/** Remove o fragmento de rotas com token antes que os providers façam requisições de sessão. */
export function prepareInitialRouteToken(): void {
  if (typeof window === 'undefined' || !tokenPaths.has(window.location.pathname)) return;
  const currentUrl = `${window.location.pathname}${window.location.search}${window.location.hash}`;
  if (preparedUrl === currentUrl) return;
  preparedToken = readAndRemoveToken();
  preparedUrl = `${window.location.pathname}${window.location.search}`;
}

/** Entrega uma única vez o token guardado em memória, ou lê o da URL se a rota foi aberta por navegação interna. */
export function takeConfirmationToken(): string | null {
  if (preparedToken !== undefined) {
    const token = preparedToken;
    preparedToken = undefined;
    return token;
  }
  return readAndRemoveToken();
}

/** Entrega uma única vez o token preparado para a resposta a um convite. */
export function takeInvitationToken(): string | null {
  if (preparedToken !== undefined) {
    const token = preparedToken;
    preparedToken = undefined;
    return token;
  }
  return readAndRemoveToken();
}
