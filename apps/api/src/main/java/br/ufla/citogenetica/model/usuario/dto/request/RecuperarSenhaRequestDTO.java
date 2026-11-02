package br.ufla.citogenetica.model.usuario.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RecuperarSenhaRequestDTO(
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email
) {}