package br.ufla.citogenetica.contagem.internal.web.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record TotalLaminasLaboratorioResponseDTO(
        UUID laboratorioId,
        @Schema(description = "Lâminas ativas que o usuário pode ver neste laboratório")
        long total) {
}
