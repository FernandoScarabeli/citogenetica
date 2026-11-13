package br.ufla.citogenetica.laboratorio.internal.application;

import java.time.Instant;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.ConviteOrientacao;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoConviteOrientacao;

public record ConviteOrientacaoResumo(
        UUID id,
        UUID laboratorioId,
        String emailDestino,
        EstadoConviteOrientacao estado,
        Instant expiraEm,
        Instant criadoEm) {

    public static ConviteOrientacaoResumo de(ConviteOrientacao convite) {
        return new ConviteOrientacaoResumo(convite.getId(), convite.getLaboratorio().getId(),
                convite.getEmailDestino(), convite.getEstado(), convite.getExpiraEm(), convite.getCreatedAt());
    }
}
