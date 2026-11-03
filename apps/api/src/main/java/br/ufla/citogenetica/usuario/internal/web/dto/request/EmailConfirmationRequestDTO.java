package br.ufla.citogenetica.usuario.internal.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailConfirmationRequestDTO(
        @NotBlank(message = "O token de confirmação é obrigatório")
        @Size(max = 128, message = "Token de confirmação inválido")
        String token) {
}
