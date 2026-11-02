import { test } from 'node:test';
import assert from 'node:assert/strict';
import { transformWithOxc } from 'vite';
import { readFileSync } from 'node:fs';
const source = readFileSync(new URL('../src/domain.ts', import.meta.url), 'utf8');
const { code: compiled } = await transformWithOxc(source, 'domain.ts');
const { emptyState, isState, normalize, pct, summary, slideGoal, removeMatching, slideCsv } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);
const slide = {id:'1',name:'L1',protocol:'mitose',species:'',treatment:'',notes:'',createdAt:'2026-10-27T12:00:00Z',entries:[
 {id:'a',phase:'Interfase',status:'normal',type:'',createdAt:'2026-10-27T12:00:00Z'},
 {id:'b',phase:'Prófase',status:'anormal',type:'Micronúcleo',createdAt:'2026-10-27T12:01:00Z'},
 {id:'c',phase:'Prófase',status:'normal',type:'',createdAt:'2026-10-27T12:02:00Z'}
]};
test('resumo deriva os totais, tipos e índice mitótico dos lançamentos',()=>{const result=summary(slide);assert.equal(result.total,3);assert.equal(result.abnormal,1);assert.equal(result.phases[1].total,2);assert.equal(result.phases[1].types['Micronúcleo'],1);assert.equal(pct(result.im,100),'66,7%');assert.equal(pct(1,result.phases[1].total),'50,0%')});
test('lâmina vazia não divide por zero',()=>{const result=summary({...slide,entries:[]});assert.equal(result.im,0);assert.equal(pct(0,0),'0,0%')});
test('normalização evita duplicação por caixa e espaços',()=>assert.equal(normalize('  MICROnúcleo  extra '),normalize('micronúcleo extra')));
test('backup reconhece estrutura válida e rejeita protocolo ou fase inválidos',()=>{assert.equal(isState({...emptyState(),slides:[slide]}),true);assert.equal(isState({...emptyState(),slides:[{...slide,protocol:'outro'}]}),false);assert.equal(isState({...emptyState(),slides:[{...slide,entries:[{...slide.entries[0],phase:'inválida'}]}]}),false)});

test('metas antigas e novas preservam o significado no backup',()=>{assert.equal(slideGoal(slide),1000);assert.equal(slideGoal({...slide,goal:null}),null);assert.equal(slideGoal({...slide,goal:250}),250);assert.equal(slideGoal({...slide,protocol:'meiose'}),null);assert.equal(isState({...emptyState(),slides:[{...slide,goal:-1}]}),false);assert.equal(isState({...emptyState(),slides:[{...slide,goal:250}]}),true)});
test('correção remove somente a fase e tipo escolhidos, sem alterar os demais registros',()=>{const before=[...slide.entries,{...slide.entries[1],id:'d',type:'Ponte'}];const after=removeMatching(before,'Prófase','anormal','Micronúcleo',1);assert.deepEqual(after.map(e=>e.id),['a','c','d']);assert.throws(()=>removeMatching(before,'Prófase','anormal','Micronúcleo',2));assert.throws(()=>removeMatching(before,'Prófase','normal','',0));assert.equal(before.length,4)});

test('CSV preserva observações com quebras de linha, ponto e vírgula e aspas',()=>{const csv=slideCsv({...slide,notes:'linha 1; "nota"\nlinha 2'});assert.ok(csv.includes('"Observações";"linha 1; ""nota""\nlinha 2"'));assert.ok(csv.includes('"Prófase";"1";"1";"2"'))});
