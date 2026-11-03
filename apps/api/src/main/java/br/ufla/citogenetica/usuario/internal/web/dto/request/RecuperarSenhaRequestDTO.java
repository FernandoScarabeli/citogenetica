package br.ufla.citogenetica.usuario.internal.web.dto.request;

import br.ufla.citogenetica.usuario.internal.domain.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RecuperarSenhaRequestDTO(
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email
) {
    public RecuperarSenhaRequestDTO {
        email = EmailNormalizer.normalize(email);
    }
}
