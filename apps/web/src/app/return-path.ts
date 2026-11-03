export function safeInternalPath(value: unknown, origin = window.location.origin): string | undefined {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) return undefined;
  if (/[\\\u0000-\u001f\u007f]/.test(value)) return undefined;

  try {
    const destination = new URL(value, origin);
    if (destination.origin !== origin || destination.username || destination.password) return undefined;
    if (destination.pathname === '/entrar' || destination.pathname.startsWith('/entrar/')) return undefined;
    return `${destination.pathname}${destination.search}${destination.hash}`;
  } catch {
    return undefined;
  }
}

export function currentInternalPath(pathname: string, search: string, hash: string): string | undefined {
  return safeInternalPath(`${pathname}${search}${hash}`);
}
