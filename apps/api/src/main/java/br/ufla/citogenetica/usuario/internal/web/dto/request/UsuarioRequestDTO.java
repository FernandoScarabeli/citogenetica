package br.ufla.citogenetica.usuario.internal.web.dto.request;

import br.ufla.citogenetica.usuario.internal.domain.EmailNormalizer;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioRequestDTO(

        @NotBlank(message = "É obrigatório informar o nome do usuário")
        String nome,

        @NotBlank(message = "É obrigatório informar o email do usuário")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "É obrigatório informar a senha do usuário")
        @Size(min = 8, message = "A Senha deve ter no minimo 8 caracteres")
        String senha,

        @NotBlank(message = "É obrigatório informar o role do usuário")
        String roleName
) {
    public UsuarioRequestDTO {
        email = EmailNormalizer.normalize(email);
    }
}
