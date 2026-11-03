package br.ufla.citogenetica.laboratorio.internal.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarConviteOrientacaoRequestDTO(
        @NotBlank(message = "Informe o e-mail de destino.")
        @Email(message = "Informe um endereço de e-mail válido.")
        @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
        String email) {
}
