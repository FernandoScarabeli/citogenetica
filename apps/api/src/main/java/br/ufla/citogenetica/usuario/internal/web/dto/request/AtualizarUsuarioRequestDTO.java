package br.ufla.citogenetica.usuario.internal.web.dto.request;

import br.ufla.citogenetica.usuario.internal.domain.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AtualizarUsuarioRequestDTO(

        @NotBlank(message = "É obrigatório informar o nome do usuário") String nome,

        @NotBlank(message = "É obrigatório informar o email do usuário") @Email(message = "Email inválido") String email
)
{
    public AtualizarUsuarioRequestDTO {
        email = EmailNormalizer.normalize(email);
    }
}
