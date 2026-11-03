package br.ufla.citogenetica.model.usuario.dto.request;

import br.ufla.citogenetica.model.usuario.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ValidarCodigoRequestDTO(
        @NotBlank(message = "Email é obrigatório") @Email(message = "Email inválido") String email,

        @NotBlank(message = "O código de verificação é obrigatório") String codigo) {
    public ValidarCodigoRequestDTO {
        email = EmailNormalizer.normalize(email);
    }
}
