import { emptyState, isState, type State } from './domain';
const KEY = 'citogenetica:workspace:v1';
export function load(): { state: State; error?: string } {
  try { const raw = localStorage.getItem(KEY); if (!raw) return {state:emptyState()}; const value: unknown = JSON.parse(raw); return isState(value) ? {state:value} : {state:emptyState(),error:'Não foi possível ler os dados salvos. Eles não serão substituídos automaticamente.'}; }
  catch { return {state:emptyState(),error:'Não foi possível acessar os dados deste navegador.'}; }
}
export function save(state: State): boolean { try { localStorage.setItem(KEY,JSON.stringify(state)); return true; } catch { return false; } }
export function download(name:string, content:string, type:string) { const url=URL.createObjectURL(new Blob([content],{type})); const a=document.createElement('a'); a.href=url; a.download=name; a.click(); setTimeout(()=>URL.revokeObjectURL(url),1000); }
