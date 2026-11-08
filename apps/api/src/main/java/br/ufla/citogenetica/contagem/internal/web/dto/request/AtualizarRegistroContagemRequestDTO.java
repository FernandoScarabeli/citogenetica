package br.ufla.citogenetica.contagem.internal.web.dto.request;

import java.util.List;

import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Substitui fase, situação, tipos e quantidade. Hora oficial, hora e fuso do aparelho nunca mudam. */
public record AtualizarRegistroContagemRequestDTO(
        @Schema(description = "Versão do registro que o cliente viu; se estiver desatualizada a resposta é 409")
        @NotNull(message = "Informe a versão esperada do registro.")
        @JsonDeserialize(using = NumeroInteiroEstrito.Longo.class)
        Long versaoEsperada,

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
        Integer quantidade) {
}
