package br.ufla.citogenetica.contagem.internal.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.StatusRegistro;

public record RegistroResumo(UUID id, Long versao, UUID laminaId, UUID faseId, StatusRegistro status,
                             List<UUID> tipoIds, int quantidade, Instant registradoEm, Instant horaAparelho,
                             String fusoAparelho) {
}
