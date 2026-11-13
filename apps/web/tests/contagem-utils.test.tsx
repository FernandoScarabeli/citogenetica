import { describe, expect, it } from 'vitest';
import { agruparPorDia, enviadaDepois, instanteExibido, mensagemDeErro, normalizarNomeTipo, textoEnvio } from '../src/contagem-utils';
import { ApiError, NetworkError } from '../src/api/errors';
import { pct } from '../src/domain';

describe('data exibida no histórico', () => {
  const oficial = '2026-11-08T15:00:00Z';
  const aparelho = '2026-10-01T10:00:00Z';

  it('usa a hora do aparelho em contagem antiga enviada do navegador (sem fuso)', () => {
    expect(instanteExibido({ registradoEm: oficial, horaAparelho: aparelho, fusoAparelho: null })).toBe(aparelho);
  });

  it('usa a hora do servidor quando a célula chegou na hora ou não há hora do aparelho', () => {
    const naHora = { registradoEm: '2026-11-08T15:00:03Z', horaAparelho: '2026-11-08T15:00:00Z', fusoAparelho: 'America/Sao_Paulo' };
    expect(enviadaDepois(naHora)).toBe(false);
    expect(instanteExibido(naHora)).toBe(naHora.registradoEm);
    expect(instanteExibido({ registradoEm: oficial, horaAparelho: null, fusoAparelho: null })).toBe(oficial);
  });

  it('célula guardada sem internet mostra a hora do clique e diz quando foi enviada', () => {
    const reenviada = { registradoEm: '2026-11-08T17:30:00Z', horaAparelho: '2026-11-08T13:05:00Z', fusoAparelho: 'America/Sao_Paulo' };
    expect(enviadaDepois(reenviada)).toBe(true);
    expect(instanteExibido(reenviada)).toBe(reenviada.horaAparelho);
    expect(textoEnvio(reenviada)).toMatch(/^enviada às \d{2}:\d{2}$/);

    const outroDia = { ...reenviada, registradoEm: '2026-11-10T17:30:00Z' };
    expect(textoEnvio(outroDia)).toMatch(/^enviada em 10 de nov de 2026 às \d{2}:\d{2}$/);
  });

  it('relógio do aparelho adiantado não troca a hora mostrada', () => {
    const adiantado = { registradoEm: '2026-11-08T15:00:00Z', horaAparelho: '2026-11-08T15:30:00Z', fusoAparelho: 'America/Sao_Paulo' };
    expect(enviadaDepois(adiantado)).toBe(false);
    expect(instanteExibido(adiantado)).toBe(adiantado.registradoEm);
  });

  it('agrupa por dia mantendo a ordem', () => {
    const base = { horaAparelho: null, fusoAparelho: 'x' };
    const groups = agruparPorDia([
      { ...base, registradoEm: '2026-11-08T15:00:00Z' },
      { ...base, registradoEm: '2026-11-08T14:00:00Z' },
      { ...base, registradoEm: '2026-11-05T14:00:00Z' },
    ]);
    expect(groups.map(group => group.registros.length)).toEqual([2, 1]);
  });
});

describe('percentuais', () => {
  it('usa duas casas decimais', () => {
    expect(pct(1, 3)).toBe('33,33%');
    expect(pct(33.3333333, 100)).toBe('33,33%');
    expect(pct(0, 0)).toBe('0,00%');
  });
});

describe('mensagens de erro', () => {
  it('usa a mensagem da API e trata falta de conexão e permissão', () => {
    expect(mensagemDeErro(new ApiError({ status: 409, error: 'Conflict', message: 'Quantidade maior que a registrada.', path: '/' }), 'x')).toBe('Quantidade maior que a registrada.');
    expect(mensagemDeErro(new ApiError({ status: 403, error: 'Forbidden', message: 'Acesso negado.', path: '/' }), 'x')).toContain('permissão');
    expect(mensagemDeErro(new NetworkError(), 'x')).toContain('conexão');
    expect(mensagemDeErro(new Error('boom'), 'padrão')).toBe('padrão');
  });
});

describe('nome normalizado do tipo de anormalidade', () => {
  it('ignora espaços, caixa e acentos, como o servidor', () => {
    expect(normalizarNomeTipo('  MICRONÚCLEO   extra ')).toBe('micronucleo extra');
    expect(normalizarNomeTipo('micronucleo')).toBe(normalizarNomeTipo('Micronúcleo'));
    expect(normalizarNomeTipo('Núcleos\tapoptóticos\n(condensados)')).toBe('nucleos apoptoticos (condensados)');
  });
});
