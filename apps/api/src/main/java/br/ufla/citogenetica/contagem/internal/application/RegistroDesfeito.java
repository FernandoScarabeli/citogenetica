package br.ufla.citogenetica.contagem.internal.application;

import java.util.UUID;

/** Registro afetado por um desfazer: {@code quantidadeRestante} é 0 quando ele foi excluído por inteiro. */
public record RegistroDesfeito(UUID id, int quantidadeRestante, boolean excluido) {
}
