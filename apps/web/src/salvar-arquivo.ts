import type { ArquivoBaixado } from './api/http-client';

/** Entrega no navegador um arquivo já baixado da API. */
export function salvarArquivo({ blob, nome }: ArquivoBaixado): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = nome;
  link.rel = 'noopener';
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
