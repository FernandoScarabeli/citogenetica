import { describe, expect, it } from 'vitest';
import { idDaCelulaLegada, idDaLaminaLegada, importarLaminasLegadas, type ProgressoImportacao } from '../src/importacao-legado';
import { interpretarBackupLegado } from '../src/leitura-legado';
import { uuidDeterministico } from '../src/migracao-laminas';
import { criarApiFalsa, lerFixture } from './api-falsa-legado';

const USUARIO = 7;
const LAB = 'aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa';
const ler = (nome: string) => interpretarBackupLegado(lerFixture(nome)).laminas;
const importar = (falsa: ReturnType<typeof criarApiFalsa>, laminas: ReturnType<typeof ler>, destino: string | null = null, onProgresso?: (p: ProgressoImportacao) => void) =>
  importarLaminasLegadas({ api: falsa.api as never, usuarioId: USUARIO, destino, laminas, onProgresso });
const idDaLamina = (laminas: ReturnType<typeof ler>, nome: string, destino: string | null = null) => idDaLaminaLegada(USUARIO, destino, laminas.find(item => item.nome === nome)!);

describe('importação dos backups antigos', () => {
  it('envia a lâmina com os dados antigos e cada célula como um registro de uma célula, na data original', async () => {
    const falsa = criarApiFalsa();
    const laminas = ler('backup_ciclo_celular.json');
    const resultado = await importar(falsa, laminas);
    expect(resultado).toEqual({
      importadas: [
        { nome: 'Controle-L1', nova: true, celulasConfirmadas: 30, celulasAcrescentadas: 30, celulasEmConflito: 0 },
        { nome: 'Tratado-L2', nova: true, celulasConfirmadas: 9, celulasAcrescentadas: 9, celulasEmConflito: 0 },
      ],
      falhas: [], naoTentadas: 0, interrupcao: null,
    });
    const id = await idDaLamina(laminas, 'Controle-L1');
    const guardada = falsa.laminas.get(id)!;
    expect(guardada.pedido).toMatchObject({ nome: 'Controle-L1', especie: 'Allium cepa', tratamento: 'Água', observacoes: 'Controle negativo.', meta: 1000, criadaEm: new Date(laminas[0].celulas[0].t).toISOString() });
    expect(guardada.pedido).not.toHaveProperty('laboratorioId');
    const [primeira] = falsa.registrosDe(id);
    expect(primeira).toMatchObject({ quantidade: 1, horaAparelho: new Date(laminas[0].celulas[0].t).toISOString() });
    expect(primeira).not.toHaveProperty('fusoAparelho');
    // Os nomes antigos viram os tipos fixos do sistema; só o que não existe no protocolo vira personalizado.
    expect(falsa.tipos.map(tipo => tipo.nome)).toEqual(['Célula binucleada']);
  });

  it('a meiose não tem meta e o id da célula usa usuário, destino, protocolo, nome, id e hora do histórico', async () => {
    const falsa = criarApiFalsa();
    const laminas = ler('backup_meiose.json');
    await importar(falsa, laminas);
    const lamina = laminas[1];
    const id = await idDaLamina(laminas, lamina.nome);
    expect(id).toBe(await uuidDeterministico(`legado|7|avulsa|meiose|${lamina.nome}`));
    expect(falsa.laminas.get(id)!.pedido.meta).toBeNull();
    const celula = lamina.celulas[0];
    expect(falsa.laminas.get(id)!.registros.has(await uuidDeterministico(`legado|7|avulsa|meiose|${lamina.nome}|${celula.idHistorico}|${celula.t}`))).toBe(true);
    expect(await idDaCelulaLegada(USUARIO, LAB, lamina, celula)).not.toBe(await idDaCelulaLegada(USUARIO, null, lamina, celula));
  });

  it('importar o mesmo arquivo de novo não cria nada', async () => {
    const falsa = criarApiFalsa();
    const laminas = ler('backup_meiose.json');
    await importar(falsa, laminas);
    const antes = { total: falsa.total(), laminas: falsa.laminas.size };
    const segunda = await importar(falsa, laminas);
    expect({ total: falsa.total(), laminas: falsa.laminas.size }).toEqual(antes);
    expect(antes).toEqual({ total: 25, laminas: 2 });
    expect(segunda.importadas).toEqual([
      { nome: 'L1-planta A', nova: false, celulasConfirmadas: 22, celulasAcrescentadas: 0, celulasEmConflito: 0 },
      { nome: 'L2 ação; "aspas"', nova: false, celulasConfirmadas: 3, celulasAcrescentadas: 0, celulasEmConflito: 0 },
    ]);
  });

  it('o backup de dias depois só acrescenta as 5 células novas', async () => {
    const falsa = criarApiFalsa();
    await importar(falsa, ler('backup_meiose.json'));
    const depois = await importar(falsa, ler('backup_meiose_depois.json'));
    expect(falsa.total()).toBe(30);
    expect(depois.importadas[0]).toEqual({ nome: 'L1-planta A', nova: false, celulasConfirmadas: 27, celulasAcrescentadas: 5, celulasEmConflito: 0 });
  });

  it('observações diferentes no segundo arquivo não impedem acrescentar células nem mudam a lâmina', async () => {
    const falsa = criarApiFalsa();
    await importar(falsa, ler('backup_meiose.json'));
    const mudado = ler('backup_meiose_depois.json').map(item => ({ ...item, observacoes: `${item.observacoes} Revisada depois.`, especie: 'Zea mays' }));
    const resultado = await importar(falsa, mudado);
    expect(resultado.falhas).toEqual([]);
    expect(resultado.importadas[0]).toMatchObject({ nova: false, celulasAcrescentadas: 5, celulasEmConflito: 0 });
    const lamina = falsa.laminas.get(await idDaLamina(mudado, 'L1-planta A'))!;
    expect(lamina.pedido.observacoes).toBe('Lâmina boa.\nAlgumas células sobrepostas.');
    expect(lamina.pedido.especie).toBe('');
    expect(lamina.registros.size).toBe(27);
  });

  it('meiose e mitose juntas não colidem, mesmo com ids de histórico repetidos entre os arquivos', async () => {
    const meiose = ler('backup_meiose.json');
    const ciclo = ler('backup_ciclo_celular.json');
    const repetidos = ciclo[0].celulas.filter(celula => meiose[0].celulas.some(outra => outra.idHistorico === celula.idHistorico));
    expect(repetidos.length).toBeGreaterThan(0);
    const falsa = criarApiFalsa();
    const resultado = await importar(falsa, [...meiose, ...ciclo]);
    expect(resultado.falhas).toEqual([]);
    expect(resultado.importadas.every(item => item.celulasEmConflito === 0)).toBe(true);
    expect(falsa.total()).toBe(25 + 39);
    expect(falsa.laminas.size).toBe(4);
  });

  it('"Ponte dupla" e "ponte  DUPLA" viram um tipo só', async () => {
    const falsa = criarApiFalsa();
    const laminas = ler('backup_meiose.json');
    await importar(falsa, laminas);
    expect(falsa.tipos.map(tipo => tipo.nome)).toEqual(['Ponte dupla']);
    const tiposUsados = falsa.registrosDe(await idDaLamina(laminas, 'L1-planta A')).flatMap(registro => registro.tipoIds ?? []);
    expect(tiposUsados.filter(id => id === falsa.tipos[0].id)).toHaveLength(2);
  });

  it('informa a célula corrigida no arquivo depois da primeira importação e mantém o que já está no sistema', async () => {
    const falsa = criarApiFalsa();
    await importar(falsa, ler('backup_meiose.json'));
    const corrigido = ler('backup_meiose.json');
    const alvo = corrigido[0].celulas.find(celula => celula.fase === 'Metáfase I')!;
    alvo.fase = 'Anáfase II';
    const antes = falsa.registrosDe(await idDaLamina(corrigido, 'L1-planta A')).map(registro => registro.faseId);
    const resultado = await importar(falsa, corrigido);
    expect(resultado.importadas[0]).toMatchObject({ celulasConfirmadas: 21, celulasEmConflito: 1, celulasAcrescentadas: 0 });
    expect(falsa.registrosDe(await idDaLamina(corrigido, 'L1-planta A')).map(registro => registro.faseId)).toEqual(antes);
  });

  it('laboratório e avulsa geram lâminas diferentes', async () => {
    const falsa = criarApiFalsa();
    const laminas = ler('backup_meiose.json');
    await importar(falsa, laminas, null);
    await importar(falsa, laminas, LAB);
    expect(falsa.laminas.size).toBe(4);
    expect(await idDaLamina(laminas, 'L1-planta A', LAB)).not.toBe(await idDaLamina(laminas, 'L1-planta A'));
    expect(falsa.laminas.get(await idDaLamina(laminas, 'L1-planta A', LAB))!.pedido.laboratorioId).toBe(LAB);
    expect(falsa.total()).toBe(50);
  });

  it('lâmina excluída no sistema não é recriada nem recebe células', async () => {
    const falsa = criarApiFalsa();
    const laminas = ler('backup_meiose.json');
    await importar(falsa, laminas);
    falsa.excluir(await idDaLamina(laminas, 'L1-planta A'));
    const mensagem = 'A lâmina "L1-planta A" foi excluída no sistema; não foi importada de novo.';
    const igual = await importar(falsa, laminas);
    expect(igual.falhas).toEqual([{ nome: 'L1-planta A', motivo: mensagem }]);
    expect(igual.importadas.map(item => item.nome)).toEqual(['L2 ação; "aspas"']);
    // Com o conteúdo da lâmina diferente (o caminho do 409) a conclusão é a mesma.
    const mudado = laminas.map(item => ({ ...item, observacoes: 'outra' }));
    expect((await importar(falsa, mudado)).falhas).toEqual([{ nome: 'L1-planta A', motivo: mensagem }]);
    expect(falsa.registrosDe(await idDaLamina(laminas, 'L1-planta A'))).toHaveLength(22);
  });

  it('lâmina com o mesmo id de outro dono ou de outro destino falha com explicação, sem alterar nada', async () => {
    const falsa = criarApiFalsa();
    const laminas = ler('backup_meiose.json');
    await importar(falsa, laminas);
    const alheia = falsa.laminas.get(await idDaLamina(laminas, 'L1-planta A'))!;
    alheia.dto.donoUsuarioId = 99;
    const resultado = await importar(falsa, laminas.map(item => ({ ...item, observacoes: 'outra' })));
    expect(resultado.falhas).toHaveLength(1);
    expect(resultado.falhas[0].motivo).toContain('Já existe no sistema uma lâmina com o mesmo identificador de "L1-planta A"');
    expect(resultado.importadas.map(item => item.nome)).toEqual(['L2 ação; "aspas"']);
    expect(falsa.total()).toBe(25);
  });

  it('queda de rede no meio para, explica, e repetir termina sem duplicar', async () => {
    const falsa = criarApiFalsa();
    const laminas = ler('backup_meiose.json');
    falsa.estado.falhaRedeAposRegistros = 10;
    const progresso: ProgressoImportacao[] = [];
    const parcial = await importar(falsa, laminas, null, p => progresso.push(p));
    expect(parcial.interrupcao).toMatch(/Sem conexão|Não foi possível conectar/);
    expect(parcial.importadas).toEqual([]);
    expect(parcial.falhas).toHaveLength(1);
    expect(parcial.naoTentadas).toBe(1);
    expect(falsa.total()).toBe(10);
    expect(progresso[0]).toEqual({ laminaIndice: 1, laminaTotal: 2, laminaNome: 'L1-planta A', celulasEnviadas: 0, celulasTotal: 22 });
    expect(progresso.at(-1)).toMatchObject({ laminaIndice: 1, celulasEnviadas: 10 });

    falsa.estado.falhaRedeAposRegistros = null;
    const completo = await importar(falsa, laminas);
    expect(completo.falhas).toEqual([]);
    expect(falsa.total()).toBe(25);
    expect(completo.importadas[0]).toMatchObject({ nova: false, celulasConfirmadas: 22, celulasAcrescentadas: 12 });
  });

  it('sessão expirada interrompe tudo e avisa', async () => {
    const falsa = criarApiFalsa();
    falsa.estado.sessaoExpirada = true;
    const resultado = await importar(falsa, ler('backup_meiose.json'));
    expect(resultado.interrupcao).toBe('Sua sessão terminou. Entre de novo para continuar.');
    expect(resultado.naoTentadas).toBe(1);
    expect(falsa.total()).toBe(0);
  });
});
