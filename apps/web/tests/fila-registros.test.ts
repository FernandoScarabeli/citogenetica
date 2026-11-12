import { beforeEach, describe, expect, it } from 'vitest';
import { ApiError, NetworkError } from '../src/api/errors';
import {
  CHAVE_FILA_REGISTROS, contarGuardados, ehRecusaDefinitiva, guardarRegistro, lerFila, removerRegistro,
} from '../src/contagem/fila-registros';

const pedido = (id: string) => ({ id, faseId: 'f1', status: 'NORMAL' as const, quantidade: 1 });
const erro = (status: number) => new ApiError({ status, error: 'Erro', message: 'x', path: '/x' });

beforeEach(() => localStorage.clear());

describe('fila de registros guardados', () => {
  it('separa por pessoa e lâmina, mantém a ordem e não duplica o mesmo id', () => {
    guardarRegistro({ usuarioId: 1, laminaId: 'a', pedido: pedido('1') });
    guardarRegistro({ usuarioId: 1, laminaId: 'a', pedido: pedido('2') });
    guardarRegistro({ usuarioId: 1, laminaId: 'a', pedido: pedido('2') });
    guardarRegistro({ usuarioId: 1, laminaId: 'b', pedido: pedido('3') });
    guardarRegistro({ usuarioId: 2, laminaId: 'a', pedido: pedido('4') });

    expect(lerFila(1, 'a').map(item => item.pedido.id)).toEqual(['1', '2']);
    expect(contarGuardados(1)).toBe(3);
    removerRegistro('1');
    removerRegistro('2');
    removerRegistro('3');
    expect(contarGuardados(1)).toBe(0);
    expect(contarGuardados(2)).toBe(1);
  });

  it('ignora conteúdo corrompido', () => {
    localStorage.setItem(CHAVE_FILA_REGISTROS, '{nao e json');
    expect(lerFila(1, 'a')).toEqual([]);
    localStorage.setItem(CHAVE_FILA_REGISTROS, JSON.stringify([{ lixo: true }]));
    expect(contarGuardados(1)).toBe(0);
  });

  it('só considera definitivas as recusas 4xx que não são 401, 408 e 429', () => {
    expect([400, 403, 404, 409, 422].every(status => ehRecusaDefinitiva(erro(status)))).toBe(true);
    expect([401, 408, 429, 500, 503].some(status => ehRecusaDefinitiva(erro(status)))).toBe(false);
    expect(ehRecusaDefinitiva(new NetworkError())).toBe(false);
  });
});
