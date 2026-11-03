package br.ufla.citogenetica.model.usuario.dto.request;

import br.ufla.citogenetica.model.usuario.EmailNormalizer;
import jakarta.validation.constraints.NotNull;

public record LoginRequestDTO(
        @NotNull()
        String email,
        @NotNull()
        String senha
) {
    public LoginRequestDTO {
        email = EmailNormalizer.normalize(email);
    }
}
