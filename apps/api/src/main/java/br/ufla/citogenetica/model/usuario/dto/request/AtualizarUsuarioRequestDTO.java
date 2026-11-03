package br.ufla.citogenetica.model.usuario.dto.request;

import br.ufla.citogenetica.model.usuario.EmailNormalizer;
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
