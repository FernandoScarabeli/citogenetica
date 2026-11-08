package br.ufla.citogenetica.contagem.internal.web.dto.request;

import java.util.List;

import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CriarRegistroContagemRequestDTO(
        @Schema(description = "UUIDv4 canônico gerado pelo cliente; repetir o mesmo id com o mesmo conteúdo é idempotente")
        @NotNull(message = "Informe o identificador do registro.")
        @CanonicalUuidV4(message = "O identificador do registro deve ser um UUIDv4 canônico.")
        String id,

        @Schema(description = "Fase da versão de protocolo da lâmina")
        @NotNull(message = "Informe a fase.")
        @CanonicalUuidV4(message = "A fase deve ser um UUIDv4 canônico.")
        String faseId,

        @Schema(description = "Situação das células do registro", allowableValues = {"NORMAL", "ANORMAL"})
        @NotNull(message = "Informe a situação do registro.")
        String status,

        @Schema(description = "Tipos de anormalidade da fase, sem repetição, de 1 a 50. Obrigatórios quando ANORMAL; "
                + "vazio ou ausente quando NORMAL", nullable = true)
        @Size(max = 50, message = "Informe no máximo 50 tipos de anormalidade.")
        List<@NotNull(message = "Tipo de anormalidade ausente.")
             @CanonicalUuidV4(message = "O tipo de anormalidade deve ser um UUIDv4 canônico.") String> tipoIds,

        @Schema(description = "Número de células do lote, inteiro de 1 até o limite por registro vigente (hoje 1000)")
        @NotNull(message = "Informe a quantidade.")
        @JsonDeserialize(using = NumeroInteiroEstrito.Inteiro.class)
        @Positive(message = "A quantidade deve ser um número inteiro positivo.")
        Integer quantidade,

        @Schema(description = "Hora do aparelho (instante ISO-8601, como 2026-11-08T12:30:00Z). Só informativa: "
                + "nunca entra em cálculo ou ordenação oficial", type = "string", format = "date-time", nullable = true)
        String horaAparelho,

        @Schema(description = "Fuso do aparelho: identificador IANA (America/Sao_Paulo) ou deslocamento (-03:00), "
                + "até 64 caracteres. Só informativo", nullable = true)
        @Size(max = 64, message = "O fuso do aparelho deve ter no máximo 64 caracteres.")
        String fusoAparelho) {
}
