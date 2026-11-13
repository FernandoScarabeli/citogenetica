package br.ufla.citogenetica.contagem.internal.application;

import java.util.UUID;

/** Tipo de anormalidade com o nome, como aparece no histórico. */
public record TipoNomeado(UUID id, String nome) {
}
