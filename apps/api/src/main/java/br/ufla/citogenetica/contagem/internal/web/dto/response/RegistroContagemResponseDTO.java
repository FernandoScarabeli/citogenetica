package br.ufla.citogenetica.contagem.internal.web.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.application.RegistroResumo;
import br.ufla.citogenetica.contagem.internal.domain.StatusRegistro;
import io.swagger.v3.oas.annotations.media.Schema;

public record RegistroContagemResponseDTO(
        UUID id,
        @Schema(description = "Versão atual do registro")
        Long versao,
        UUID laminaId,
        UUID faseId,
        StatusRegistro status,
        @Schema(description = "Tipos de anormalidade em ordem crescente do UUID; vazio quando NORMAL")
        List<UUID> tipoIds,
        @Schema(description = "Número de células do lote")
        int quantidade,
        @Schema(description = "Hora oficial, definida pelo servidor")
        Instant registradoEm,
        @Schema(description = "Hora informada pelo aparelho, só informativa", nullable = true)
        Instant horaAparelho,
        @Schema(description = "Fuso informado pelo aparelho, só informativo", nullable = true)
        String fusoAparelho) {

    public static RegistroContagemResponseDTO de(RegistroResumo resumo) {
        return new RegistroContagemResponseDTO(resumo.id(), resumo.versao(), resumo.laminaId(), resumo.faseId(),
                resumo.status(), resumo.tipoIds(), resumo.quantidade(), resumo.registradoEm(),
                resumo.horaAparelho(), resumo.fusoAparelho());
    }
}
