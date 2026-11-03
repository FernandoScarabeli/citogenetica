package br.ufla.citogenetica.laboratorio.internal.application;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.ConviteLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoConviteLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;

public record ConviteLaboratorioResumo(
        UUID id,
        UUID laboratorioId,
        String emailDestino,
        Set<PapelLaboratorio> papeis,
        EstadoConviteLaboratorio estado,
        Instant expiraEm,
        Instant criadoEm) {

    public ConviteLaboratorioResumo {
        papeis = Set.copyOf(papeis);
    }

    public static ConviteLaboratorioResumo de(ConviteLaboratorio convite) {
        EnumSet<PapelLaboratorio> papeis = EnumSet.of(PapelLaboratorio.MEMBRO);
        if (convite.isConcederAdmin()) {
            papeis.add(PapelLaboratorio.ADMIN);
        }
        if (convite.isConcederOrientador()) {
            papeis.add(PapelLaboratorio.ORIENTADOR);
        }
        return new ConviteLaboratorioResumo(convite.getId(), convite.getLaboratorio().getId(),
                convite.getEmailDestino(), papeis, convite.getEstado(), convite.getExpiraEm(), convite.getCreatedAt());
    }
}
