package br.ufla.citogenetica.usuario.internal.web.dto.request;

import br.ufla.citogenetica.usuario.internal.domain.EmailNormalizer;
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
