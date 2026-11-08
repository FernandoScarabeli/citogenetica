package br.ufla.citogenetica.contagem.internal.application;

import java.util.List;

/** Item do histórico: o registro atual e os tipos com nome (o nome de um tipo personalizado só o dono conhece). */
public record RegistroHistorico(RegistroResumo registro, List<TipoNomeado> tipos) {
}
