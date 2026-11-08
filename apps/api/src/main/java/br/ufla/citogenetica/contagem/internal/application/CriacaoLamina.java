package br.ufla.citogenetica.contagem.internal.application;

/** Resultado da criação: {@code criada} é falso quando a requisição foi uma repetição idempotente. */
public record CriacaoLamina(LaminaResumo lamina, boolean criada) {
}
