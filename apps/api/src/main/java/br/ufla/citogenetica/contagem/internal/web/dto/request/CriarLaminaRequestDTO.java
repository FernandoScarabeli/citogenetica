package br.ufla.citogenetica.contagem.internal.web.dto.request;

import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CriarLaminaRequestDTO(
        @Schema(description = "UUIDv4 canônico gerado pelo cliente; repetir o mesmo id com o mesmo conteúdo é idempotente")
        @NotNull(message = "Informe o identificador da lâmina.")
        @CanonicalUuidV4(message = "O identificador da lâmina deve ser um UUIDv4 canônico.")
        String id,

        @Schema(description = "Versão publicada do protocolo usada na contagem; não pode mudar depois")
        @NotNull(message = "Informe a versão do protocolo.")
        @CanonicalUuidV4(message = "A versão do protocolo deve ser um UUIDv4 canônico.")
        String protocoloVersaoId,

        @Schema(description = "Laboratório da lâmina; ausente significa lâmina avulsa (só o dono vê). Não pode mudar depois",
                nullable = true)
        @CanonicalUuidV4(message = "O laboratório deve ser um UUIDv4 canônico.")
        String laboratorioId,

        @Schema(description = "Nome da lâmina, obrigatório, até 120 caracteres depois de colapsar espaços")
        @NotNull(message = "Informe o nome da lâmina.")
        String nome,

        @Schema(description = "Espécie, opcional, até 120 caracteres; ausente vira texto vazio", nullable = true)
        String especie,

        @Schema(description = "Tratamento, opcional, até 120 caracteres; ausente vira texto vazio", nullable = true)
        String tratamento,

        @Schema(description = "Observações, opcionais, até 5000 caracteres; mantêm as quebras de linha", nullable = true)
        String observacoes,

        @Schema(description = "Meta de células da lâmina: inteiro positivo; ausente = sem meta", nullable = true)
        @JsonDeserialize(using = NumeroInteiroEstrito.Inteiro.class)
        @Positive(message = "A meta deve ser um número inteiro positivo.")
        Integer meta) {
}
