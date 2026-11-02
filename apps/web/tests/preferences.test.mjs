import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { transformWithOxc } from 'vite';
const {code}=await transformWithOxc(readFileSync(new URL('../src/preferences.ts',import.meta.url),'utf8'),'preferences.ts');
const {loadPreferences,savePreferences}=await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);
test('preferências são recuperadas e dados inválidos são preservados',()=>{
 const records=new Map();
 globalThis.localStorage={getItem:key=>records.get(key)??null,setItem:(key,value)=>records.set(key,value)};
 try {
  assert.deepEqual(loadPreferences().value,{name:'Nome Sobrenome',dark:false});
  savePreferences({name:'Ana',dark:true});
  assert.deepEqual(loadPreferences(),{value:{name:'Ana',dark:true},error:''});
  const photo='data:image/png;base64,aGVsbG8=';
  savePreferences({name:'Ana',dark:true,photo});
  assert.deepEqual(loadPreferences().value,{name:'Ana',dark:true,photo});
  records.set('citogenetica:preferences:v1',JSON.stringify({name:'Ana',dark:true,photo:'https://invalid.example/image.png'}));
  assert.notEqual(loadPreferences().error,'');
  assert.match(records.get('citogenetica:preferences:v1'),/invalid/);
  records.set('citogenetica:preferences:v1','{inválido');
  assert.notEqual(loadPreferences().error,'');
  assert.equal(records.get('citogenetica:preferences:v1'),'{inválido');
 } finally { delete globalThis.localStorage; }
});
