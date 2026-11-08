package br.ufla.citogenetica.contagem.internal.web.dto.request;

import java.util.List;

import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DesfazerRegistroRequestDTO(
        @Schema(description = "UUIDv4 canônico da operação, gerado pelo cliente; repetir o mesmo id com o mesmo pedido "
                + "devolve a resposta original sem remover células de novo")
        @NotNull(message = "Informe o identificador da operação.")
        @CanonicalUuidV4(message = "O identificador da operação deve ser um UUIDv4 canônico.")
        String id,

        @Schema(description = "Fase da versão de protocolo da lâmina")
        @NotNull(message = "Informe a fase.")
        @CanonicalUuidV4(message = "A fase deve ser um UUIDv4 canônico.")
        String faseId,

        @Schema(description = "Situação dos registros a desfazer", allowableValues = {"NORMAL", "ANORMAL"})
        @NotNull(message = "Informe a situação do registro.")
        String status,

        @Schema(description = "Conjunto exato de tipos dos registros a desfazer, sem repetição, de 1 a 50. Obrigatórios "
                + "quando ANORMAL; vazio ou ausente quando NORMAL", nullable = true)
        @Size(max = 50, message = "Informe no máximo 50 tipos de anormalidade.")
        List<@NotNull(message = "Tipo de anormalidade ausente.")
             @CanonicalUuidV4(message = "O tipo de anormalidade deve ser um UUIDv4 canônico.") String> tipoIds,

        @Schema(description = "Quantidade de células a desfazer, inteiro positivo. Pode passar do limite por registro: "
                + "vale o que estiver disponível")
        @NotNull(message = "Informe a quantidade.")
        @JsonDeserialize(using = NumeroInteiroEstrito.Inteiro.class)
        @Positive(message = "A quantidade deve ser um número inteiro positivo.")
        Integer quantidade) {
}
