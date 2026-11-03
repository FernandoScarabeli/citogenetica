package br.ufla.citogenetica.shared.web.dto;

import jakarta.validation.constraints.NotNull;

public record GenericDTO(
        @NotNull(message = "O campo Id é obrigatório.") Long id,
        @NotNull(message = "O campo version é obrigatório.") Long version
) {}
