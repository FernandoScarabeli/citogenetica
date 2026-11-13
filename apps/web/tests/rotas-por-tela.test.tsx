import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation, useNavigate } from 'react-router';
import { ApiError } from '../src/api/errors';
import type { LaboratorioResponseDTO, ParticipacaoResponseDTO } from '../src/api/contracts/laboratorio';
import type { LaminaDTO, ProtocoloDTO } from '../src/api/contracts/contagem';
import type { UsuarioResponseDTO } from '../src/api/contracts/usuario';

const labApi = vi.hoisted(() => ({
  listarMeus: vi.fn(), buscar: vi.fn(), criar: vi.fn(), atualizarNome: vi.fn(),
  listarParticipacoes: vi.fn(), alterarPapeis: vi.fn(), removerParticipacao: vi.fn(), sair: vi.fn(),
}));
const conviteApi = vi.hoisted(() => ({ criar: vi.fn(), listar: vi.fn(), reenviar: vi.fn(), revogar: vi.fn(), aceitar: vi.fn(), recusar: vi.fn() }));
const orientacaoApi = vi.hoisted(() => ({
  listarVinculos: vi.fn(), encerrarVinculo: vi.fn(), criarConvite: vi.fn(), listarConvites: vi.fn(),
  reenviarConvite: vi.fn(), cancelarConvite: vi.fn(), aceitarConvite: vi.fn(), recusarConvite: vi.fn(),
}));
vi.mock('../src/api/laboratorio-api', () => ({ laboratorioApi: labApi }));
vi.mock('../src/api/convite-api', () => ({ conviteApi }));
vi.mock('../src/api/orientacao-api', () => ({ orientacaoApi }));

import { Laboratories } from '../src/Laboratories';
import { Slides } from '../src/Slides';

const USER: UsuarioResponseDTO = {
  id: 12, version: 0, createdDate: '2026-11-03T12:00:00', lastModifiedDate: null, nome: 'Ana Pesquisadora',
  email: 'ana@ufla.br', status: 'ATIVO',
  role: { id: 1, version: 0, createdDate: '2026-11-03T12:00:00', lastModifiedDate: null, nome: 'USER' },
};
const LAB: LaboratorioResponseDTO = { id: 'lab-1', nome: 'Citogenética Vegetal', estado: 'ATIVO', papeis: ['MEMBRO', 'ADMIN'], versao: 1 };
const SELF: ParticipacaoResponseDTO = { id: 'p-1', nome: USER.nome, email: USER.email, papeis: ['MEMBRO', 'ADMIN'], versao: 1 };
const PROTOCOLOS: ProtocoloDTO[] = [{
  id: 'p-mitose', codigo: 'mitose', nome: 'Mitose',
  versaoAtual: { id: 'v-mitose', numero: 1, fases: [{ id: 'f1', nome: 'Interfase', ordem: 1, contaComoDivisao: false, tipos: [] }] },
}];
const LAMINA: LaminaDTO = {
  id: 'lamina-1', versao: 3, donoUsuarioId: USER.id, donoNome: USER.nome, laboratorioId: LAB.id, protocoloVersaoId: 'v-mitose',
  nome: 'L1 planta A', especie: 'Allium cepa', tratamento: 'controle', observacoes: 'Nota salva', meta: 1000, totalCelulas: 0,
  criadaEm: '2026-11-03T12:00:00Z', atualizadaEm: '2026-11-03T12:00:00Z',
};
const page = <T,>(content: T[]) => ({ content, currentPage: 0, totalPages: 1, totalElements: content.length });

const contagem = {
  listarProtocolos: vi.fn(), buscarVersaoProtocolo: vi.fn(), listarTiposPersonalizados: vi.fn(), criarTipoPersonalizado: vi.fn(),
  listarLaminas: vi.fn(), contarLaminasPorLaboratorio: vi.fn(), buscarLamina: vi.fn(), criarLamina: vi.fn(), atualizarLamina: vi.fn(), excluirLamina: vi.fn(),
  criarRegistro: vi.fn(), listarRegistros: vi.fn(), atualizarRegistro: vi.fn(), desfazerRegistros: vi.fn(), buscarResumo: vi.fn(),
};

/** Mostra o caminho atual e oferece o "Voltar" do navegador. */
function Sonda() {
  const location = useLocation();
  const navigate = useNavigate();
  return <>
    <div data-testid="caminho">{location.pathname}{location.search}</div>
    <button type="button" onClick={() => navigate(-1)}>Voltar do navegador</button>
  </>;
}

function abrir(caminho: string) {
  return render(<MemoryRouter initialEntries={[caminho]}>
    <Sonda/>
    <Laboratories
      user={USER}
      onLogout={vi.fn()}
      onUserUpdate={vi.fn()}
      api={contagem as never}
      renderSlides={(lab, onBack, readOnly) => <Slides key={lab?.id ?? 'avulsas'} laboratory={lab} onBack={onBack} readOnly={readOnly} usuarioId={USER.id} api={contagem as never}/>}
    />
  </MemoryRouter>);
}

const caminho = () => screen.getByTestId('caminho').textContent;

beforeEach(() => {
  localStorage.clear();
  vi.resetAllMocks();
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value(this: HTMLDialogElement) { this.setAttribute('open', ''); } });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value(this: HTMLDialogElement) { this.removeAttribute('open'); } });
  labApi.listarMeus.mockResolvedValue([LAB]);
  contagem.contarLaminasPorLaboratorio.mockResolvedValue(new Map());
  labApi.buscar.mockResolvedValue(LAB);
  labApi.listarParticipacoes.mockResolvedValue([SELF]);
  conviteApi.listar.mockResolvedValue([]);
  orientacaoApi.listarVinculos.mockResolvedValue([]);
  orientacaoApi.listarConvites.mockResolvedValue([]);
  contagem.listarProtocolos.mockResolvedValue(PROTOCOLOS);
  contagem.listarLaminas.mockResolvedValue(page([LAMINA]));
  contagem.buscarLamina.mockResolvedValue(LAMINA);
  contagem.buscarResumo.mockResolvedValue({ laminaId: LAMINA.id, total: 0, anormais: 0, indiceMitotico: 0, fases: [] });
  contagem.listarRegistros.mockResolvedValue(page([]));
  contagem.listarTiposPersonalizados.mockResolvedValue([]);
  contagem.buscarVersaoProtocolo.mockResolvedValue(PROTOCOLOS[0].versaoAtual);
});

describe('cada tela com endereço próprio', () => {
  it('/laboratorios mostra Meus laboratórios', async () => {
    abrir('/laboratorios');
    expect(await screen.findByRole('heading', { name: 'Meus laboratórios' })).toBeTruthy();
  });

  it('/laboratorios/:id/laminas mostra as lâminas do laboratório', async () => {
    abrir('/laboratorios/lab-1/laminas');
    expect(await screen.findByRole('heading', { name: 'Lâminas' })).toBeTruthy();
    expect(contagem.listarLaminas).toHaveBeenCalledWith(expect.objectContaining({ laboratorioId: 'lab-1' }));
    expect(await screen.findByText('L1 planta A')).toBeTruthy();
  });

  it('/laboratorios/:id/laminas/:laminaId abre a lâmina direto', async () => {
    abrir('/laboratorios/lab-1/laminas/lamina-1');
    expect(await screen.findByRole('heading', { name: 'L1 planta A' })).toBeTruthy();
  });

  it('/laminas-avulsas e /laminas-avulsas/:laminaId funcionam sem laboratório', async () => {
    const avulsa = { ...LAMINA, laboratorioId: null };
    contagem.listarLaminas.mockResolvedValue(page([avulsa]));
    contagem.buscarLamina.mockResolvedValue(avulsa);
    const primeira = abrir('/laminas-avulsas');
    expect(await screen.findByText('L1 planta A')).toBeTruthy();
    expect(contagem.listarLaminas).toHaveBeenCalledWith(expect.not.objectContaining({ laboratorioId: expect.anything() }));
    primeira.unmount();

    abrir('/laminas-avulsas/lamina-1');
    expect(await screen.findByRole('heading', { name: 'L1 planta A' })).toBeTruthy();
  });

  it('/laboratorios/:id/gerenciar abre a equipe e ?aba= escolhe a aba', async () => {
    const primeira = abrir('/laboratorios/lab-1/gerenciar');
    expect(await screen.findByRole('heading', { name: 'Equipe do laboratório' })).toBeTruthy();
    primeira.unmount();

    abrir('/laboratorios/lab-1/gerenciar?aba=configuracoes');
    expect(await screen.findByRole('heading', { name: 'Dados do laboratório' })).toBeTruthy();
  });

  it('trocar de aba atualiza o endereço sem empilhar histórico', async () => {
    const user = userEvent.setup();
    abrir('/laboratorios');
    await user.click(await screen.findByRole('button', { name: /^Gerenciar/ }));
    await screen.findByRole('heading', { name: 'Equipe do laboratório' });
    await user.click(screen.getByRole('button', { name: 'Configurações' }));
    expect(caminho()).toBe('/laboratorios/lab-1/gerenciar?aba=configuracoes');

    await user.click(screen.getByRole('button', { name: 'Voltar do navegador' }));
    expect(await screen.findByRole('heading', { name: 'Meus laboratórios' })).toBeTruthy();
  });

  it('/perfil abre as configurações do perfil', async () => {
    abrir('/perfil');
    expect(await screen.findByRole('heading', { name: 'Seu perfil' })).toBeTruthy();
  });
});

describe('navegação empilha e o Voltar do navegador volta uma tela', () => {
  it('lista -> lâminas -> lâmina -> Voltar -> Voltar', async () => {
    const user = userEvent.setup();
    abrir('/laboratorios');
    await user.click(await screen.findByRole('button', { name: LAB.nome }));
    expect(caminho()).toBe('/laboratorios/lab-1/laminas');

    await user.click(await screen.findByRole('button', { name: /L1 planta A/ }));
    expect(await screen.findByRole('heading', { name: 'L1 planta A' })).toBeTruthy();
    expect(caminho()).toBe('/laboratorios/lab-1/laminas/lamina-1');

    await user.click(screen.getByRole('button', { name: 'Voltar do navegador' }));
    expect(await screen.findByRole('heading', { name: 'Lâminas' })).toBeTruthy();
    expect(caminho()).toBe('/laboratorios/lab-1/laminas');

    await user.click(screen.getByRole('button', { name: 'Voltar do navegador' }));
    expect(await screen.findByRole('heading', { name: 'Meus laboratórios' })).toBeTruthy();
  });

  it('criar lâmina cai direto na lâmina criada, no endereço dela', async () => {
    const user = userEvent.setup();
    contagem.criarLamina.mockResolvedValue({ ...LAMINA, id: 'nova', nome: 'Nova lâmina' });
    contagem.buscarLamina.mockResolvedValue({ ...LAMINA, id: 'nova', nome: 'Nova lâmina' });
    abrir('/laboratorios/lab-1/laminas');
    await user.click(await screen.findByRole('button', { name: /Nova lâmina/ }));
    await user.type(screen.getByLabelText(/Identificação da lâmina/), 'Nova lâmina');
    await user.click(screen.getByRole('button', { name: /Criar lâmina/ }));
    expect(await screen.findByRole('heading', { name: 'Nova lâmina' })).toBeTruthy();
    expect(caminho()).toBe('/laboratorios/lab-1/laminas/nova');
  });

  it('sair do laboratório volta à lista com aviso', async () => {
    const user = userEvent.setup();
    labApi.sair.mockResolvedValue(undefined);
    abrir('/laboratorios/lab-1/gerenciar?aba=configuracoes');
    await user.click(await screen.findByRole('button', { name: 'Sair do laboratório' }));
    await user.click(screen.getAllByRole('button', { name: 'Sair do laboratório' }).at(-1)!);
    expect(await screen.findByRole('heading', { name: 'Meus laboratórios' })).toBeTruthy();
    expect(caminho()).toBe('/laboratorios');
    expect((await screen.findByRole('status')).textContent).toContain('Você saiu de Citogenética Vegetal');
  });
});

describe('endereço inválido ou sem acesso', () => {
  it('lâmina que não está na lista mostra "não encontrada" com caminho de volta', async () => {
    abrir('/laboratorios/lab-1/laminas/outra-lamina');
    expect(await screen.findByRole('heading', { name: 'Lâmina não encontrada' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Ver as lâminas' }).getAttribute('href')).toBe('/laboratorios/lab-1/laminas');
  });

  it.each([404, 403])('laboratório com erro %i na tela de lâminas volta à lista com aviso', async status => {
    labApi.buscar.mockRejectedValue(new ApiError({ status, error: 'Erro', message: 'x', path: '/api/v1/laboratorios/lab-x' }));
    abrir('/laboratorios/lab-x/laminas');
    expect((await screen.findByRole('status')).textContent).toContain('Este laboratório não está mais disponível');
    expect(await screen.findByRole('heading', { name: 'Meus laboratórios' })).toBeTruthy();
    expect(caminho()).toBe('/laboratorios');
  });

  it('laboratório inexistente na tela de gerenciar volta à lista com aviso', async () => {
    labApi.buscar.mockRejectedValue(new ApiError({ status: 404, error: 'Erro', message: 'x', path: '/api/v1/laboratorios/lab-x' }));
    abrir('/laboratorios/lab-x/gerenciar');
    expect((await screen.findByRole('status')).textContent).toContain('Este laboratório não está mais disponível');
    expect(await screen.findByRole('heading', { name: 'Meus laboratórios' })).toBeTruthy();
  });
});

describe('observações ao sair da lâmina pela navegação', () => {
  it('salva o texto pendente ao voltar para a lista de lâminas', async () => {
    const user = userEvent.setup();
    contagem.atualizarLamina.mockResolvedValue({ ...LAMINA, versao: 4, observacoes: 'Nota salva e mais' });
    abrir('/laboratorios/lab-1/laminas/lamina-1');
    await screen.findByRole('heading', { name: 'L1 planta A' });
    await user.type(screen.getByLabelText('Observações'), ' e mais');
    await user.click(screen.getByRole('button', { name: 'Lâminas' }));
    expect(await screen.findByRole('heading', { name: 'Lâminas' })).toBeTruthy();
    expect(caminho()).toBe('/laboratorios/lab-1/laminas');
    await waitFor(() => expect(contagem.atualizarLamina).toHaveBeenCalledWith('lamina-1', expect.objectContaining({ observacoes: 'Nota salva e mais' })));
  });
});
