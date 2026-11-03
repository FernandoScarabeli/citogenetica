export const LEGACY_LABORATORIES_KEY = 'citogenetica:laboratories:v1';

export function clearLegacyLaboratoryData(storage?: Pick<Storage, 'removeItem'>): void {
  try {
    (storage ?? globalThis.localStorage).removeItem(LEGACY_LABORATORIES_KEY);
  } catch {
    // A remoção da configuração antiga não deve interromper a abertura da aplicação.
  }
}
