package br.ufla.citogenetica.model.usuario.dto.request;

import jakarta.validation.constraints.NotNull;

public record LoginRequestDTO(
        @NotNull()
        String email,
        @NotNull()
        String senha
) {}
