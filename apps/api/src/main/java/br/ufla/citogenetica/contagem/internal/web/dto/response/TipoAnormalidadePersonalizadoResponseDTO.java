package br.ufla.citogenetica.contagem.internal.web.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record TipoAnormalidadePersonalizadoResponseDTO(
        UUID id,
        UUID faseId,
        @Schema(description = "Nulo quando o tipo vale para lâminas avulsas", nullable = true)
        UUID laboratorioId,
        String nome) {
}
