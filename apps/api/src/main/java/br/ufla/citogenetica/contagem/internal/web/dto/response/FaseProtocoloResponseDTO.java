package br.ufla.citogenetica.contagem.internal.web.dto.response;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record FaseProtocoloResponseDTO(
        UUID id,
        String nome,
        int ordem,
        @Schema(description = "Falso apenas para fases que não entram no índice mitótico, como Interfase")
        boolean contaComoDivisao,
        @Schema(description = "Tipos de anormalidade fixos da fase, em ordem")
        List<TipoAnormalidadeFixoResponseDTO> tipos) {
}
