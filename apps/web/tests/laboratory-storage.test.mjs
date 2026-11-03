import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { transformWithOxc } from 'vite';

const storageSource = readFileSync(new URL('../src/storage/legacy-laboratories.ts', import.meta.url), 'utf8');
const domainSource = readFileSync(new URL('../src/domain.ts', import.meta.url), 'utf8');
const [{ code: storageCode }, { code: domainCode }] = await Promise.all([
  transformWithOxc(storageSource, 'legacy-laboratories.ts'),
  transformWithOxc(domainSource, 'domain.ts'),
]);
const toModule = code => import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);
const storage = await toModule(storageCode);
const domain = await toModule(domainCode);

test('remove somente a chave antiga de laboratórios e preserva lâminas e preferências', () => {
  const values = new Map([
    [storage.LEGACY_LABORATORIES_KEY, '{"version":1}'],
    ['citogenetica:workspace:v1', '{"slides":[]}'],
    ['citogenetica:preferences:v1', '{"dark":true}'],
  ]);
  storage.clearLegacyLaboratoryData({ removeItem: key => values.delete(key) });

  assert.equal(values.has(storage.LEGACY_LABORATORIES_KEY), false);
  assert.equal(values.get('citogenetica:workspace:v1'), '{"slides":[]}');
  assert.equal(values.get('citogenetica:preferences:v1'), '{"dark":true}');
});

test('falha ao limpar armazenamento antigo não interrompe a aplicação', () => {
  assert.doesNotThrow(() => storage.clearLegacyLaboratoryData({
    removeItem() { throw new Error('armazenamento indisponível'); },
  }));
});

test('lâminas com identificador local antigo continuam em avulsas e não vazam para UUID real', () => {
  const slides = [
    { id: 'sem-lab', labId: undefined },
    { id: 'antiga', labId: 'laboratorio-local-antigo' },
    { id: 'outra-antiga', labId: 'demo-genetica' },
    { id: 'atual', labId: '8d8c8657-599d-4d5e-9f27-f4d62d3a043a' },
  ];

  assert.deepEqual(domain.slidesForLaboratory(slides, null).map(slide => slide.id), [
    'sem-lab', 'antiga', 'outra-antiga',
  ]);
  assert.deepEqual(domain.slidesForLaboratory(slides, '8d8c8657-599d-4d5e-9f27-f4d62d3a043a').map(slide => slide.id), ['atual']);
});
