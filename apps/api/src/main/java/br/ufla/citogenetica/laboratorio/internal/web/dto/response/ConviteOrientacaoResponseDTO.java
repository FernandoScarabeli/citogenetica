package br.ufla.citogenetica.laboratorio.internal.web.dto.response;

import java.time.Instant;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.EstadoConviteOrientacao;

public record ConviteOrientacaoResponseDTO(
        UUID id,
        UUID laboratorioId,
        String emailDestino,
        EstadoConviteOrientacao estado,
        Instant expiraEm,
        Instant criadoEm) {
}
