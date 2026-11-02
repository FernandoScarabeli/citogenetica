export const demoUser = { id: 'demo-user', name: 'Você', email: 'pesquisador@exemplo.com' };
export const roles = ['Administrador', 'Orientador', 'Pesquisador'] as const;
export type Role = typeof roles[number];
/** Earlier roles include the capabilities of every role after them. */
export const highestRole = (assigned: readonly Role[]): Role | undefined => roles.find(role => assigned.includes(role));
export const hasRole = (assigned: readonly Role[], required: Role): boolean => { const highest = highestRole(assigned); return highest !== undefined && roles.indexOf(highest) <= roles.indexOf(required); };
export type Member = { id: string; name: string; email: string; roles: Role[]; active: boolean };
export type Laboratory = { id: string; name: string; institution: string; description?: string; members: Member[] };
export type Invitation = { id: string; labId: string; email: string; roles: Role[]; status: 'Pendente'|'Aceito'|'Revogado'|'Recusado' };
export type LaboratoriesState = { version: 1; laboratories: Laboratory[]; invitations: Invitation[] };
export const participation = (lab: Laboratory) => lab.members.find(m => m.id === demoUser.id && m.active);
export const isLastAdmin = (lab: Laboratory, member: Member) => member.active && hasRole(member.roles, 'Administrador') && lab.members.filter(m => m.active && hasRole(m.roles, 'Administrador')).length === 1;
export type InvitationDraft = { email: string; role: Role };
export function validateInvitationDrafts(drafts: InvitationDraft[], lab: Laboratory, invitations: Invitation[]): string {
 const addresses=drafts.map(d=>d.email.trim().toLowerCase());
 if(!addresses.length || addresses.some(address=>!address)) return 'Informe o e-mail de cada pessoa.';
 if(new Set(addresses).size!==addresses.length) return 'Cada e-mail deve aparecer apenas uma vez.';
 if(addresses.some(address=>lab.members.some(member=>member.active && member.email.toLowerCase()===address))) return 'Uma das pessoas já participa do laboratório.';
 if(addresses.some(address=>invitations.some(invite=>invite.labId===lab.id && invite.status==='Pendente' && invite.email.toLowerCase()===address))) return 'Já existe um convite pendente para um desses e-mails.';
 return '';
}
export function demoLaboratories(): LaboratoriesState {
 return { version: 1, laboratories: [
  { id: 'demo-citogenetica', name: 'Laboratório de Citogenética', institution: 'UFLA · Universidade Federal de Lavras', members: [
   { ...demoUser, roles: ['Administrador'], active: true },
   { id: 'demo-marina', name: 'Marina Alves', email: 'marina@exemplo.com', roles: ['Orientador'], active: true },
   { id: 'demo-rafael', name: 'Rafael Lima', email: 'rafael@exemplo.com', roles: ['Pesquisador'], active: true }
  ] },
  { id: 'demo-genetica', name: 'Genética Vegetal', institution: 'Grupo de pesquisa', members: [
   { id: 'demo-clara', name: 'Clara Costa', email: 'clara@exemplo.com', roles: ['Administrador'], active: true }
  ] }
 ], invitations: [{ id: 'demo-invitation', labId: 'demo-genetica', email: demoUser.email, roles: ['Pesquisador'], status: 'Pendente' }] };
}
const KEY = 'citogenetica:laboratories:v1';
export function loadLaboratories(): { state: LaboratoriesState; error: string } {
 try { const raw = localStorage.getItem(KEY); if (!raw) return {state: demoLaboratories(), error: ''};
 const state = JSON.parse(raw);
 if (state.version !== 1 || !Array.isArray(state.laboratories) || !Array.isArray(state.invitations) || !state.laboratories.every((l: Laboratory) => typeof l.id === 'string' && typeof l.name === 'string' && typeof l.institution === 'string' && (l.description === undefined || typeof l.description === 'string') && Array.isArray(l.members) && l.members.every(m => typeof m.id === 'string' && typeof m.name === 'string' && typeof m.email === 'string' && typeof m.active === 'boolean' && Array.isArray(m.roles) && m.roles.length && m.roles.every(r => roles.includes(r)))) || !state.invitations.every((i: Invitation) => typeof i.id === 'string' && typeof i.labId === 'string' && typeof i.email === 'string' && ['Pendente','Aceito','Revogado','Recusado'].includes(i.status) && Array.isArray(i.roles) && i.roles.length && i.roles.every(r => roles.includes(r)))) throw Error();
 return {state, error: ''};
 } catch { return {state: {version:1,laboratories:[],invitations:[]}, error:'Não foi possível ler os laboratórios salvos. Os dados existentes foram preservados. Reabra em um navegador com armazenamento disponível.'}; }
}
export function saveLaboratories(state: LaboratoriesState) { localStorage.setItem(KEY, JSON.stringify(state)); }
