package br.ufla.citogenetica.usuario.internal.web.dto.request;

import br.ufla.citogenetica.usuario.internal.domain.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RedefinirSenhaRequestDTO(
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "O código de verificação é obrigatório")
        String codigo,

        @NotNull(message = "É obrigatório informar a nova senha")
        String novaSenha
) {
    public RedefinirSenhaRequestDTO {
        email = EmailNormalizer.normalize(email);
    }
}
