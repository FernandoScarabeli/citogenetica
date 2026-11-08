package br.ufla.citogenetica.contagem.internal.application;

import java.util.UUID;

public record TipoPersonalizadoResumo(UUID id, UUID faseId, UUID laboratorioId, String nome) {
}
