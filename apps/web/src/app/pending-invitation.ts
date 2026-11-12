// Guarda o convite enquanto a pessoa cria a conta e confirma o e-mail (muitas vezes em outra aba),
// para que, ao entrar, ela volte ao convite sem precisar abrir o e-mail de novo.
// Fica no localStorage (vale para todas as abas) e some sozinho depois de pouco tempo.

export type InvitationKind = 'laboratory' | 'orientation';

export interface PendingInvitation {
  token: string;
  kind: InvitationKind;
}

const STORAGE_KEY = 'citogenetica.convite-pendente';
export const PENDING_INVITATION_TTL_MS = 2 * 60 * 60 * 1000;

export function invitationPath(kind: InvitationKind): string {
  return kind === 'orientation' ? '/orientacoes/convites/aceitar' : '/convites/aceitar';
}

export function savePendingInvitation(invitation: PendingInvitation, now = Date.now()): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...invitation, expiresAt: now + PENDING_INVITATION_TTL_MS }));
  } catch {
    // Sem armazenamento disponível: a pessoa só precisará abrir o link do convite de novo.
  }
}

export function readPendingInvitation(now = Date.now()): PendingInvitation | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const value = JSON.parse(raw) as Partial<PendingInvitation> & { expiresAt?: unknown };
    const validKind = value.kind === 'laboratory' || value.kind === 'orientation';
    if (typeof value.token !== 'string' || !validKind || typeof value.expiresAt !== 'number' || value.expiresAt <= now) {
      clearPendingInvitation();
      return null;
    }
    return { token: value.token, kind: value.kind as InvitationKind };
  } catch {
    clearPendingInvitation();
    return null;
  }
}

export function clearPendingInvitation(): void {
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Nada a limpar.
  }
}
