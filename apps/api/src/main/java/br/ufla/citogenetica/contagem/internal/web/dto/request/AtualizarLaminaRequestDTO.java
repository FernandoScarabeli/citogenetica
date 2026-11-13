package br.ufla.citogenetica.contagem.internal.web.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Substitui os campos descritivos. Protocolo, laboratório e dono nunca mudam. */
public record AtualizarLaminaRequestDTO(
        @Schema(description = "Versão da lâmina que o cliente viu; se estiver desatualizada a resposta é 409")
        @NotNull(message = "Informe a versão esperada da lâmina.")
        @JsonDeserialize(using = NumeroInteiroEstrito.Longo.class)
        Long versaoEsperada,

        @Schema(description = "Nome da lâmina, obrigatório, até 120 caracteres depois de colapsar espaços")
        @NotNull(message = "Informe o nome da lâmina.")
        String nome,

        @Schema(description = "Espécie, até 120 caracteres; ausente ou vazia limpa o campo", nullable = true)
        String especie,

        @Schema(description = "Tratamento, até 120 caracteres; ausente ou vazio limpa o campo", nullable = true)
        String tratamento,

        @Schema(description = "Observações, até 5000 caracteres; ausentes ou vazias limpam o campo", nullable = true)
        String observacoes,

        @Schema(description = "Meta de células: inteiro positivo; nula remove a meta", nullable = true)
        @JsonDeserialize(using = NumeroInteiroEstrito.Inteiro.class)
        @Positive(message = "A meta deve ser um número inteiro positivo.")
        Integer meta) {
}
