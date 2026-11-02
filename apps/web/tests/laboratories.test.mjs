import { test } from 'node:test';
import assert from 'node:assert/strict';
import { transformWithOxc } from 'vite';
import { readFileSync } from 'node:fs';
const {code}=await transformWithOxc(readFileSync(new URL('../src/laboratories.ts',import.meta.url),'utf8'),'laboratories.ts');
const {demoLaboratories,isLastAdmin,participation,demoUser,hasRole}=await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);
test('laboratório preserva último administrador, inclusive ao acumular papéis',()=>{const lab=demoLaboratories().laboratories[0];const me=participation(lab);assert.equal(isLastAdmin(lab,me),true);assert.equal(hasRole(me.roles,'Pesquisador'),true);const others={...lab,members:[...lab.members,{id:'other',name:'Outro admin',email:'outro@exemplo.com',roles:['Administrador'],active:true}]};assert.equal(isLastAdmin(others,me),false);others.members.at(-1).active=false;assert.equal(isLastAdmin(others,me),true);});
test('participação inativa e convite pendente não concedem acesso',()=>{const data=demoLaboratories();assert.equal(participation(data.laboratories[1]),undefined);const lab=data.laboratories[0];assert.equal(participation({...lab,members:lab.members.map(m=>m.id===demoUser.id?{...m,active:false}:m)}),undefined);assert.equal(lab.members.length,3);});

test('descrição opcional preserva dados existentes e é validada ao carregar', async () => {
 const {loadLaboratories,saveLaboratories}=await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);
 const records=new Map();
 globalThis.localStorage={getItem:key=>records.get(key)??null,setItem:(key,value)=>records.set(key,value)};
 try {
  const existing=demoLaboratories();
  saveLaboratories(existing);
  assert.deepEqual(loadLaboratories().state,existing);
  existing.laboratories[0].description='Pesquisa em citogenética vegetal';
  saveLaboratories(existing);
  assert.equal(loadLaboratories().state.laboratories[0].description,'Pesquisa em citogenética vegetal');
  existing.laboratories[0].description=42;
  saveLaboratories(existing);
  assert.notEqual(loadLaboratories().error,'');
  assert.equal(JSON.parse(records.get('citogenetica:laboratories:v1')).laboratories[0].description,42);
 } finally { delete globalThis.localStorage; }
});

test('hierarquia inclui permissões dos níveis inferiores e respeita registros antigos', async () => {
 const {highestRole,hasRole}=await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);
 assert.equal(highestRole(['Pesquisador','Administrador']), 'Administrador');
 assert.equal(highestRole(['Pesquisador','Orientador']), 'Orientador');
 assert.equal(hasRole(['Administrador'],'Orientador'), true);
 assert.equal(hasRole(['Administrador'],'Pesquisador'), true);
 assert.equal(hasRole(['Orientador'],'Pesquisador'), true);
 assert.equal(hasRole(['Orientador'],'Administrador'), false);
 assert.equal(hasRole(['Pesquisador'],'Orientador'), false);
 assert.equal(hasRole([],'Pesquisador'), false);
});

test('validação de convites em lote evita duplicatas e preserva o escopo do laboratório', async () => {
 const {validateInvitationDrafts}=await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);
 const data=demoLaboratories(), lab=data.laboratories[0];
 const draft=(email)=>({email,role:'Pesquisador'});
 assert.equal(validateInvitationDrafts([draft('nova@exemplo.com'),draft('outra@exemplo.com')],lab,data.invitations),'');
 assert.notEqual(validateInvitationDrafts([draft(' A@exemplo.com '),draft('a@exemplo.com')],lab,data.invitations),'');
 assert.notEqual(validateInvitationDrafts([draft('MARINA@EXEMPLO.COM')],lab,data.invitations),'');
 const pending={id:'pending',labId:lab.id,email:'PENDENTE@exemplo.com',roles:['Pesquisador'],status:'Pendente'};
 assert.notEqual(validateInvitationDrafts([draft('pendente@exemplo.com')],lab,[pending]),'');
 assert.equal(validateInvitationDrafts([draft('pendente@exemplo.com')],lab,[{...pending,labId:'outro'}]),'');
 assert.equal(validateInvitationDrafts([draft('pendente@exemplo.com')],lab,[{...pending,status:'Revogado'}]),'');
});
