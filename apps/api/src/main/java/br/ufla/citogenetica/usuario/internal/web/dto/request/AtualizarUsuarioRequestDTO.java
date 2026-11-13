package br.ufla.citogenetica.usuario.internal.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizarUsuarioRequestDTO(
        @NotBlank(message = "É obrigatório informar o nome do usuário")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String nome) {
}
