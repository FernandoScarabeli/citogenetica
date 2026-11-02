package br.ufla.citogenetica.model.usuario.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedefinirSenhaRequestDTO(
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "O código de verificação é obrigatório")
        String codigo,

        @NotBlank(message = "É obrigatório informar a nova senha")
        @Size(min = 8, message = "A nova senha deve ter no mínimo 8 caracteres")
        String novaSenha
) {}