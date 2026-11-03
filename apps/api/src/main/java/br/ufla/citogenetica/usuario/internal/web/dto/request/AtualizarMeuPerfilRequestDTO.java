package br.ufla.citogenetica.usuario.internal.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AtualizarMeuPerfilRequestDTO(
        @NotBlank(message = "É obrigatório informar o nome do usuário")
        String nome
) {}
