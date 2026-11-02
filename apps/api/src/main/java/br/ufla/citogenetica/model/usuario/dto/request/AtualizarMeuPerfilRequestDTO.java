package br.ufla.citogenetica.model.usuario.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AtualizarMeuPerfilRequestDTO(
        @NotBlank(message = "É obrigatório informar o nome do usuário")
        String nome
) {}
