package br.ufla.citogenetica.laboratorio.internal.application;

import java.time.Instant;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.VinculoOrientacao;

public record VinculoOrientacaoResumo(
        UUID id,
        UUID laboratorioId,
        Long orientadorUsuarioId,
        String nomeOrientador,
        Long pesquisadorUsuarioId,
        String nomePesquisador,
        Instant criadoEm) {

    public static VinculoOrientacaoResumo de(VinculoOrientacao vinculo,
                                              String nomeOrientador, String nomePesquisador) {
        return new VinculoOrientacaoResumo(vinculo.getId(), vinculo.getLaboratorio().getId(),
                vinculo.getOrientadorUsuarioId(), nomeOrientador,
                vinculo.getPesquisadorUsuarioId(), nomePesquisador, vinculo.getCreatedAt());
    }
}
