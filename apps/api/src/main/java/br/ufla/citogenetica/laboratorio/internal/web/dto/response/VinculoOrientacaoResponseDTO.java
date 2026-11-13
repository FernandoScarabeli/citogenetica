package br.ufla.citogenetica.laboratorio.internal.web.dto.response;

import java.time.Instant;
import java.util.UUID;

public record VinculoOrientacaoResponseDTO(
        UUID id,
        UUID laboratorioId,
        Long orientadorUsuarioId,
        String nomeOrientador,
        Long pesquisadorUsuarioId,
        String nomePesquisador,
        Instant criadoEm) {
}
