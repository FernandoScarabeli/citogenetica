package br.ufla.citogenetica.laboratorio.internal.web.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.EstadoConviteLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;

public record ConviteLaboratorioResponseDTO(
        UUID id,
        UUID laboratorioId,
        String emailDestino,
        Set<PapelLaboratorio> papeis,
        EstadoConviteLaboratorio estado,
        Instant expiraEm,
        Instant criadoEm) {
}
