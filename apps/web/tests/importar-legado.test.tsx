import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import type { LaboratorioResponseDTO } from '../src/api/contracts/laboratorio';
import { Slides } from '../src/Slides';
import { criarApiFalsa, lerFixture } from './api-falsa-legado';

const LAB: LaboratorioResponseDTO = { id: 'aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa', nome: 'Citogenética Vegetal', estado: 'ATIVO', papeis: ['MEMBRO'], versao: 1 };

function preparar(props: { laboratory?: LaboratorioResponseDTO | null; readOnly?: boolean } = {}) {
  const falsa = criarApiFalsa(12);
  const listar = vi.spyOn(falsa.api, 'listarLaminas');
  render(<MemoryRouter><Slides laboratory={props.laboratory ?? null} onBack={vi.fn()} readOnly={props.readOnly ?? false} usuarioId={12} api={falsa.api as never} /></MemoryRouter>);
  return { falsa, listar };
}
const arquivo = (nome: string, conteudo = lerFixture(nome)) => new File([conteudo], nome, { type: 'application/json' });
const botaoMenu = () => screen.findByRole('button', { name: /^Importar/ });

async function escolher(user: ReturnType<typeof userEvent.setup>, file: File) {
  await user.click(await botaoMenu());
  await user.click(screen.getByRole('menuitem', { name: 'Importar backup antigo' }));
  const dialogo = await screen.findByRole('dialog');
  await user.upload(within(dialogo).getByLabelText(/Arquivo de backup/), file);
  return dialogo;
}

beforeEach(() => {
  localStorage.clear();
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value(this: HTMLDialogElement) { this.setAttribute('open', ''); } });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value(this: HTMLDialogElement) { this.removeAttribute('open'); } });
});

describe('importar backup antigo na lista de lâminas', () => {
  it('com a lista vazia, o menu só oferece importar, e só para quem pode criar lâminas', async () => {
    const user = userEvent.setup();
    const { falsa } = preparar();
    const menu = await botaoMenu();
    expect(menu.textContent).toBe('Importar');
    expect(falsa.laminas.size).toBe(0);
    await user.click(menu);
    expect(screen.getAllByRole('menuitem').map(item => item.textContent)).toEqual(['Importar backup antigo']);
  });

  it('não mostra o botão em laboratório desativado (somente leitura)', async () => {
    preparar({ laboratory: { ...LAB, estado: 'DESATIVADO' }, readOnly: true });
    await screen.findByRole('heading', { name: 'Lâminas' });
    expect(screen.queryByRole('button', { name: /Importar/ })).toBeNull();
  });

  it('prévia, importação e resultado, com a lista recarregada no fim (laboratório)', async () => {
    const user = userEvent.setup();
    const { falsa, listar } = preparar({ laboratory: LAB });
    const dialogo = await escolher(user, arquivo('backup_meiose.json'));
    expect(await within(dialogo).findByText(/2 lâminas podem ser importadas no laboratório Citogenética Vegetal/)).toBeTruthy();
    const lista = within(dialogo).getByRole('list', { name: 'Lâminas que serão importadas' });
    expect(within(lista).getByText('L1-planta A')).toBeTruthy();
    expect(within(lista).getByText('Meiose · 22 células')).toBeTruthy();
    expect(within(lista).getByText(/tipo de anormalidade não existe no protocolo e será criado como tipo personalizado: Ponte dupla \(Anáfase I\)/)).toBeTruthy();
    const chamadasAntes = listar.mock.calls.length;

    await user.click(within(dialogo).getByRole('button', { name: 'Importar 2 lâminas' }));
    expect(await within(dialogo).findByText('Importação concluída.')).toBeTruthy();
    expect(within(dialogo).getByText('2 lâminas novas')).toBeTruthy();
    expect(falsa.total()).toBe(25);
    expect([...falsa.laminas.values()].every(item => item.pedido.laboratorioId === LAB.id)).toBe(true);
    await waitFor(() => expect(listar.mock.calls.length).toBeGreaterThan(chamadasAntes));
    await user.click(within(dialogo).getAllByRole('button', { name: 'Fechar' }).at(-1)!);
    expect(await screen.findByText('L1-planta A')).toBeTruthy();
    expect(localStorage.length).toBe(0);
  });

  it('o backup de depois informa as células acrescentadas e importa como avulsa na lista avulsa', async () => {
    const user = userEvent.setup();
    const { falsa } = preparar();
    let dialogo = await escolher(user, arquivo('backup_meiose.json'));
    expect(await within(dialogo).findByText(/2 lâminas podem ser importadas como lâminas avulsas/)).toBeTruthy();
    await user.click(within(dialogo).getByRole('button', { name: 'Importar 2 lâminas' }));
    await within(dialogo).findByText('Importação concluída.');
    await user.click(within(dialogo).getAllByRole('button', { name: 'Fechar' }).at(-1)!);

    dialogo = await escolher(user, arquivo('backup_meiose_depois.json'));
    await user.click(await within(dialogo).findByRole('button', { name: 'Importar 2 lâminas' }));
    expect(await within(dialogo).findByText(/L1-planta A \(5 células acrescentadas\)/)).toBeTruthy();
    expect(falsa.total()).toBe(30);
  });

  it('a prévia explica as lâminas que não serão importadas e os nomes trocados', async () => {
    const user = userEvent.setup();
    preparar();
    const ciclo = JSON.parse(lerFixture('backup_ciclo_celular.json'));
    ciclo.laminas.Estranha = { phases: { 'Fase inventada': { normal: 1, abnormalTypes: {} } }, history: [{ id: 'x', phase: 'Fase inventada', status: 'Normal', type: null, t: 1741608014000 }], obs: '' };
    ciclo.laminas.Divergente = { phases: { Interfase: { normal: 3, abnormalTypes: {} } }, history: [{ id: 'y', phase: 'Interfase', status: 'Normal', type: null, t: 1741608014000 }], obs: '' };
    const dialogo = await escolher(user, arquivo('ciclo.json', JSON.stringify(ciclo)));
    expect(await within(dialogo).findByText(/2 lâminas não serão importadas/)).toBeTruthy();
    const recusadas = within(dialogo).getByRole('list', { name: 'Lâminas que não serão importadas' });
    expect(within(recusadas).getByText(/Fase desconhecida: "Fase inventada"/)).toBeTruthy();
    expect(within(recusadas).getByText(/As contagens somam 3 células, mas o histórico tem 1/)).toBeTruthy();
    expect(within(dialogo).getByText(/"Stickness" será importado como "Stickiness"/)).toBeTruthy();
    expect((within(dialogo).getByRole('button', { name: 'Importar 2 lâminas' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('arquivo inválido mostra o motivo e deixa escolher outro', async () => {
    const user = userEvent.setup();
    preparar();
    const dialogo = await escolher(user, arquivo('quebrado.json', '{ nada'));
    expect((await within(dialogo).findByRole('alert')).textContent).toContain('não é um JSON válido');
    await user.upload(within(dialogo).getByLabelText(/Arquivo de backup/), arquivo('backup_ciclo_celular.json'));
    expect(await within(dialogo).findByText(/2 lâminas podem ser importadas/)).toBeTruthy();
  });

  it('erro de rede no meio para, explica, e "Tentar de novo" termina sem duplicar', async () => {
    const user = userEvent.setup();
    const { falsa } = preparar();
    falsa.estado.falhaRedeAposRegistros = 8;
    const dialogo = await escolher(user, arquivo('backup_meiose.json'));
    await user.click(await within(dialogo).findByRole('button', { name: 'Importar 2 lâminas' }));
    expect(await within(dialogo).findByText('A importação não terminou por completo.')).toBeTruthy();
    expect(within(dialogo).getByRole('alert').textContent).toMatch(/Sem conexão com o servidor/);
    expect(falsa.total()).toBe(8);

    falsa.estado.falhaRedeAposRegistros = null;
    await user.click(within(dialogo).getByRole('button', { name: 'Tentar de novo' }));
    expect(await within(dialogo).findByText('Importação concluída.')).toBeTruthy();
    expect(falsa.total()).toBe(25);
  });

  it('célula alterada no arquivo e lâmina excluída aparecem no resultado', async () => {
    const user = userEvent.setup();
    const { falsa } = preparar();
    let dialogo = await escolher(user, arquivo('backup_meiose.json'));
    await user.click(await within(dialogo).findByRole('button', { name: 'Importar 2 lâminas' }));
    await within(dialogo).findByText('Importação concluída.');
    await user.click(within(dialogo).getAllByRole('button', { name: 'Fechar' }).at(-1)!);
    const [primeira] = [...falsa.laminas.keys()];
    falsa.excluir(primeira);

    const alterado = JSON.parse(lerFixture('backup_meiose.json'));
    alterado.laminas['L2 ação; "aspas"'].history[0].phase = 'Tétrade';
    dialogo = await escolher(user, arquivo('alterado.json', JSON.stringify(alterado)));
    await user.click(await within(dialogo).findByRole('button', { name: 'Importar 2 lâminas' }));
    expect(await within(dialogo).findByText('A importação não terminou por completo.')).toBeTruthy();
    expect(within(dialogo).getByText(/foi excluída no sistema; não foi importada de novo/)).toBeTruthy();
    expect(within(dialogo).getByText(/1 célula foi alterada no arquivo depois da primeira importação/)).toBeTruthy();
    expect(within(dialogo).getByText('Ficou valendo o que já está no sistema.')).toBeTruthy();
  });
});
