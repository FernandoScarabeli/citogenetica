package br.ufla.citogenetica.usuario.internal.web.dto.response;

import br.ufla.citogenetica.usuario.internal.domain.Role;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;

import java.time.LocalDateTime;

public record UsuarioResponseDTO(
        Long id,
        Long version,
        LocalDateTime createdDate,
        LocalDateTime lastModifiedDate,

        String nome,
        String email,
        StatusEnum status,
        Role role
) {}