package br.ufla.citogenetica.contagem.internal.application;

/** Resultado da criação: {@code criado} é falso quando a requisição foi uma repetição idempotente. */
public record CriacaoTipoPersonalizado(TipoPersonalizadoResumo tipo, boolean criado) {
}
