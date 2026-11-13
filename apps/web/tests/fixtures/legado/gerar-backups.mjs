// Gera backups fictícios dos HTMLs antigos, usando as próprias páginas (cliques reais nas funções da página).
// Uso: node gerar-backups.mjs <pasta-docs> <pasta-saida>
// Precisa do playwright-core e de um Chromium baixado (~/.cache/ms-playwright), instalados FORA do repo
// (por exemplo: npm i playwright-core numa pasta temporária e copiar este arquivo para lá).
// Gera backup_meiose.json, backup_meiose_depois.json e backup_ciclo_celular.json a partir dos HTMLs de docs/.
import { chromium } from 'playwright-core';
import { mkdirSync, copyFileSync } from 'node:fs';
import { join } from 'node:path';
import { homedir } from 'node:os';

const [docs, saida] = process.argv.slice(2);
mkdirSync(saida, { recursive: true });

// Relógio e sorteio determinísticos: o mesmo script gera sempre os mesmos ids e horas.
const initScript = () => {
  let t = Date.parse('2025-03-10T12:00:00Z');
  window.__avancar = ms => { t += ms; };
  Date.now = () => { t += 7000; return t; };
  let s = 42;
  Math.random = () => { s = (s * 1103515245 + 12345) % 2147483648; return s / 2147483648; };
};

const browser = await chromium.launch({
  executablePath: join(homedir(), '.cache/ms-playwright/chromium-1254/chrome-linux64/chrome'),
});

async function pagina(arquivo) {
  const context = await browser.newContext({ acceptDownloads: true });
  await context.addInitScript(initScript);
  const page = await context.newPage();
  await page.goto('file://' + join(docs, arquivo));
  return { context, page };
}

async function abrirLamina(page, nome, meta = {}) {
  await page.fill('#slideName', nome);
  await page.evaluate(() => loadOrCreateSlide());
  if (meta.especie !== undefined) {
    await page.fill('#especieField', meta.especie);
    await page.fill('#tratamentoField', meta.tratamento);
    await page.evaluate(() => saveMeta());
  }
  if (meta.obs) {
    await page.fill('#obsField', meta.obs);
    await page.evaluate(() => saveObs());
  }
}

const normal = (page, fase, n = 1) => page.evaluate(([f, k]) => { for (let i = 0; i < k; i++) logNormal(f); }, [fase, n]);
async function anormal(page, fase, tipo, outro) {
  await page.evaluate(([f, t, o]) => {
    openAbnormalModal(f);
    const sel = document.getElementById('abnormalType');
    sel.value = t;
    sel.dispatchEvent(new Event('change'));
    if (o) document.getElementById('customType').value = o;
    confirmAbnormal();
  }, [fase, tipo, outro ?? null]);
}
const dias = (page, n) => page.evaluate(ms => window.__avancar(ms), n * 86400000);
// Corrige um registro antigo: muda a fase (mantém a data original), como a pessoa faria pelo histórico.
const editarFase = (page, indice, novaFase) => page.evaluate(([i, f]) => {
  const entry = data.history[i];
  openEditHistory(entry.id);
  document.getElementById('editPhase').value = f;
  document.getElementById('editPhase').dispatchEvent(new Event('change'));
  document.getElementById('editStatus').value = entry.status;
  document.getElementById('editStatus').dispatchEvent(new Event('change'));
  if (entry.status === 'Anormal') document.getElementById('editType').value = entry.type;
  confirmEditHistory();
}, [indice, novaFase]);

async function baixarBackup(page, nome) {
  const [download] = await Promise.all([page.waitForEvent('download'), page.evaluate(() => exportBackup())]);
  const destino = join(saida, nome);
  copyFileSync(await download.path(), destino);
  console.log('gerado', destino);
}

// ---------- Meiose ----------
{
  const { context, page } = await pagina('registro-meiose.html');
  await abrirLamina(page, 'L1-planta A', { obs: 'Lâmina boa.\nAlgumas células sobrepostas.' });
  await normal(page, 'Leptóteno/Zigóteno', 6);
  await normal(page, 'Paquíteno', 4);
  await anormal(page, 'Diplóteno/Diacinese', 'Univalente');
  await anormal(page, 'Diplóteno/Diacinese', 'Univalente');
  await dias(page, 1);
  await normal(page, 'Metáfase I', 5);
  await anormal(page, 'Anáfase I', 'Outro (especificar)', 'Ponte dupla');
  await anormal(page, 'Anáfase I', 'Outro (especificar)', 'ponte  DUPLA'); // reaproveita o tipo "Outro" já criado
  await normal(page, 'Tétrade', 3);
  await anormal(page, 'Tétrade', 'Tríade');
  await page.evaluate(() => undoLast()); // desfaz a Tríade
  await editarFase(page, 0, 'Paquíteno'); // primeira célula corrigida de fase
  await abrirLamina(page, 'L2 ação; "aspas"');
  await normal(page, 'Telófase II', 2);
  await anormal(page, 'Telófase II', 'Ponte');
  await abrirLamina(page, 'L3 vazia');
  await baixarBackup(page, 'backup_meiose.json');
  // Dias depois, a pessoa continua contando a L1 e baixa outro backup: importar os dois não pode duplicar nada.
  await dias(page, 5);
  await abrirLamina(page, 'L1-planta A');
  await normal(page, 'Metáfase II', 4);
  await anormal(page, 'Metáfase II', 'Assincronia');
  await baixarBackup(page, 'backup_meiose_depois.json');
  await context.close();
}

// ---------- Ciclo celular (mitose) ----------
{
  const { context, page } = await pagina('registro-Ciclo_celular.html');
  await abrirLamina(page, 'Controle-L1', { especie: 'Allium cepa', tratamento: 'Água', obs: 'Controle negativo.' });
  await normal(page, 'Interfase', 20);
  await anormal(page, 'Interfase', 'Núcleos apoptóticos (condensados)');
  await normal(page, 'Prófase', 3);
  await anormal(page, 'Prófase', 'Stickness');
  await dias(page, 2);
  await normal(page, 'Metáfase', 2);
  await anormal(page, 'Anáfase', 'Pontes com fragmento');
  await anormal(page, 'Telófase', 'Micronúcleo: +de 3');
  await anormal(page, 'Telófase', 'Outro (especificar)', 'Célula binucleada');
  await abrirLamina(page, 'Tratado-L2', { especie: 'Allium cepa', tratamento: 'Glifosato 1%' });
  await normal(page, 'Interfase', 8);
  await anormal(page, 'Metáfase', 'Metáfase colchicínica');
  await baixarBackup(page, 'backup_ciclo_celular.json');
  await context.close();
}

await browser.close();
