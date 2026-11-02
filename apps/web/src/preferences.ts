const KEY = 'citogenetica:preferences:v1';
export type Preferences = { name: string; dark: boolean; photo?: string };
export function loadPreferences(): {value: Preferences; error: string} {
 const fallback = {name:'Nome Sobrenome',dark:false};
 try {
  const raw = localStorage.getItem(KEY);
  if (!raw) return {value:fallback,error:''};
  const value: unknown = JSON.parse(raw);
  if (!value || typeof value !== 'object' || !('name' in value) || typeof value.name !== 'string' || !('dark' in value) || typeof value.dark !== 'boolean' || ('photo' in value && (typeof value.photo !== 'string' || !/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(value.photo) || value.photo.length > 2800000))) throw new Error();
  return {value:{name:value.name.trim() || 'Nome Sobrenome',dark:value.dark,...('photo' in value ? {photo:value.photo as string} : {})},error:''};
 } catch { return {value:fallback,error:'Não foi possível ler as preferências salvas. Os dados existentes foram preservados.'}; }
}
export function savePreferences(value: Preferences): void { localStorage.setItem(KEY, JSON.stringify(value)); }
