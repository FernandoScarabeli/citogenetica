import { describe, expect, it } from 'vitest';
import { ErroDeArquivoLegado, LIMITE_BYTES, interpretarBackupLegado, lerBackupLegado, tiposPersonalizados } from '../src/leitura-legado';
import { PROTOCOLOS_REAIS, lerFixture } from './api-falsa-legado';

const backup = (laminas: object) => JSON.stringify({ exportadoEm: '2026-11-13T00:00:00Z', laminas });
const celula = (id: string, phase: string, status = 'Normal', type: string | null = null, t = 1741608014000) => ({ id, phase, status, type, t });
const lamina = (history: object[], phases: object, extra: object = {}) => ({ phases, history, obs: '', ...extra });

describe('leitura dos backups antigos', () => {
  it('lê o backup da meiose: protocolo, células e nomes com ; e aspas', () => {
    const { laminas, recusadas } = interpretarBackupLegado(lerFixture('backup_meiose.json'));
    expect(recusadas).toEqual([]);
    expect(laminas.map(item => [item.nome, item.protocolo, item.celulas.length])).toEqual([['L1-planta A', 'meiose', 22], ['L2 ação; "aspas"', 'meiose', 3]]);
    expect(laminas[0].observacoes).toBe('Lâmina boa.\nAlgumas células sobrepostas.');
    expect(laminas[0].celulas.map(item => item.t)).toEqual([...laminas[0].celulas.map(item => item.t)].sort((a, b) => a - b));
    const tipos = laminas[0].celulas.filter(item => item.fase === 'Anáfase I').map(item => item.tipo);
    expect(tipos.sort()).toEqual(['Ponte dupla', 'ponte  DUPLA']);
  });

  it('lê o backup da mitose, com espécie e tratamento, e troca os nomes antigos pelos do sistema', () => {
    const { laminas } = interpretarBackupLegado(lerFixture('backup_ciclo_celular.json'));
    expect(laminas.map(item => [item.nome, item.protocolo, item.celulas.length, item.especie, item.tratamento])).toEqual([
      ['Controle-L1', 'mitose', 30, 'Allium cepa', 'Água'], ['Tratado-L2', 'mitose', 9, 'Allium cepa', 'Glifosato 1%'],
    ]);
    const controle = laminas[0];
    const tipos = new Set(controle.celulas.filter(item => item.status === 'anormal').map(item => item.tipo));
    expect(tipos).toEqual(new Set(['Núcleos apoptóticos (condensados)', 'Stickiness', 'Ponte com fragmento', 'Micronúcleo: mais de 3', 'Célula binucleada']));
    expect(controle.renomeados).toEqual(expect.arrayContaining([
      { de: 'Stickness', para: 'Stickiness' }, { de: 'Pontes com fragmento', para: 'Ponte com fragmento' }, { de: 'Micronúcleo: +de 3', para: 'Micronúcleo: mais de 3' },
    ]));
    expect(tiposPersonalizados(controle, PROTOCOLOS_REAIS)).toEqual(['Célula binucleada (Telófase)']);
  });

  it('lê o segundo backup da meiose com 5 células a mais na L1', () => {
    const { laminas } = interpretarBackupLegado(lerFixture('backup_meiose_depois.json'));
    expect(laminas.map(item => item.celulas.length)).toEqual([27, 3]);
  });

  it('recusa arquivo inválido, vazio, sem lâminas e grande demais', async () => {
    expect(() => interpretarBackupLegado('')).toThrow('O arquivo está vazio.');
    expect(() => interpretarBackupLegado('   \n')).toThrow('vazio');
    expect(() => interpretarBackupLegado('{ não é json')).toThrow(/JSON válido/);
    expect(() => interpretarBackupLegado('[1,2]')).toThrow(/não há lâminas/);
    expect(() => interpretarBackupLegado('{"laminas":[]}')).toThrow(/não há lâminas/);
    expect(() => interpretarBackupLegado('{"laminas":{}}')).toThrow('O backup não tem nenhuma lâmina.');
    expect(() => interpretarBackupLegado('x'.repeat(LIMITE_BYTES + 1))).toThrow(ErroDeArquivoLegado);
    await expect(lerBackupLegado({ size: LIMITE_BYTES + 1, text: async () => { throw new Error('não deveria ler'); } })).rejects.toThrow(/grande demais/);
    await expect(lerBackupLegado({ size: 10, text: async () => { throw new Error('x'); } })).rejects.toThrow(/Não foi possível abrir/);
  });

  it('deixa a lâmina de fora quando há fase desconhecida, e diz qual', () => {
    const { laminas, recusadas } = interpretarBackupLegado(backup({
      ok: lamina([celula('a', 'Interfase')], { Interfase: { normal: 1, abnormalTypes: {} } }),
      ruim: lamina([celula('b', 'Fase inventada')], { 'Fase inventada': { normal: 1, abnormalTypes: {} } }),
    }));
    expect(laminas.map(item => item.nome)).toEqual(['ok']);
    expect(recusadas).toEqual([{ nome: 'ruim', motivo: expect.stringContaining('Fase desconhecida: "Fase inventada"') }]);
  });

  it('deixa a lâmina de fora quando a soma das contagens não bate com o histórico', () => {
    const { laminas, recusadas } = interpretarBackupLegado(backup({
      divergente: lamina([celula('a', 'Interfase'), celula('b', 'Interfase')], { Interfase: { normal: 1, abnormalTypes: {} } }),
      certa: lamina([celula('c', 'Prófase', 'Anormal', 'Micronúcleo')], { Prófase: { normal: 0, abnormalTypes: { Micronúcleo: 1 } } }),
    }));
    expect(laminas.map(item => item.nome)).toEqual(['certa']);
    expect(recusadas[0].motivo).toContain('As contagens somam 1 células, mas o histórico tem 2');
  });

  it('recusa lâmina sem células, com fases de meiose e mitose juntas, id repetido ou hora inválida', () => {
    const { laminas, recusadas } = interpretarBackupLegado(backup({
      vazia: lamina([], { Interfase: { normal: 0, abnormalTypes: {} } }),
      mista: lamina([celula('a', 'Interfase'), celula('b', 'Paquíteno')], { Interfase: { normal: 1, abnormalTypes: {} }, Paquíteno: { normal: 1, abnormalTypes: {} } }),
      repetida: lamina([celula('a', 'Interfase'), celula('a', 'Interfase')], { Interfase: { normal: 2, abnormalTypes: {} } }),
      semHora: lamina([{ ...celula('a', 'Interfase'), t: 'ontem' }], { Interfase: { normal: 1, abnormalTypes: {} } }),
    }));
    expect(laminas).toEqual([]);
    expect(recusadas.map(item => item.motivo)).toEqual([
      expect.stringContaining('nenhuma célula'), expect.stringContaining('misturam meiose e mitose'), expect.stringContaining('aparece mais de uma vez'), expect.stringContaining('data e hora válidas'),
    ]);
  });

  it('não deixa passar nomes de lâmina fora dos limites da API nem nomes que só diferem em espaços', () => {
    const uma = lamina([celula('a', 'Interfase')], { Interfase: { normal: 1, abnormalTypes: {} } });
    const { laminas, recusadas } = interpretarBackupLegado(backup({ 'L1  A': uma, 'L1 A': uma, ['n'.repeat(121)]: uma }));
    expect(laminas.map(item => item.nome)).toEqual(['L1 A']);
    expect(recusadas).toHaveLength(2);
  });
});
