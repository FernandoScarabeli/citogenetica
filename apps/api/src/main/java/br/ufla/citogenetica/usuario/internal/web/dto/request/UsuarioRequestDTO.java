package br.ufla.citogenetica.usuario.internal.web.dto.request;

import br.ufla.citogenetica.usuario.internal.domain.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioRequestDTO(

        @NotBlank(message = "É obrigatório informar o nome do usuário")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String nome,

        @NotBlank(message = "É obrigatório informar o email do usuário")
        @Email(message = "Email inválido")
        String email,

        @NotNull(message = "É obrigatório informar a senha do usuário")
        String senha
) {
    public UsuarioRequestDTO {
        email = EmailNormalizer.normalize(email);
    }
}
