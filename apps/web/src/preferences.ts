const KEY = 'citogenetica:preferences:v1';
/** Preferências deste navegador. A foto de perfil fica no servidor, por conta (ver `fotoPerfilUrl`). */
export type Preferences = { dark: boolean };
export function loadPreferences(): {value: Preferences; error: string} {
 const fallback: Preferences = {dark:false};
 try {
  const raw = localStorage.getItem(KEY);
  if (!raw) return {value:fallback,error:''};
  const value: unknown = JSON.parse(raw);
  if (!value || typeof value !== 'object' || !('dark' in value) || typeof value.dark !== 'boolean') throw new Error();
  // Versões antigas guardavam aqui uma foto única para qualquer conta deste navegador: ela é descartada.
  if ('photo' in value) { try { savePreferences({dark:value.dark}); } catch { /* sem armazenamento: some na próxima gravação */ } }
  return {value:{dark:value.dark},error:''};
 } catch { return {value:fallback,error:'Não foi possível ler as preferências salvas. Os dados existentes foram preservados.'}; }
}
export function savePreferences(value: Preferences): void { localStorage.setItem(KEY, JSON.stringify({dark:value.dark})); }
