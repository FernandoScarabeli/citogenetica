package br.ufla.citogenetica.contagem.internal.application;

import java.util.List;
import java.util.UUID;

/** Resposta de um desfazer; na repetição idempotente é a resposta original, sem reler os registros. */
public record ResultadoDesfazer(UUID id, int quantidadeDesfeita, List<RegistroDesfeito> registros) {
}
