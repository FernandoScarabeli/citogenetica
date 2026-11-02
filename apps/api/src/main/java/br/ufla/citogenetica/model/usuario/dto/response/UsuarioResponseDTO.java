package br.ufla.citogenetica.model.usuario.dto.response;

import br.ufla.citogenetica.model.roles.Role;
import br.ufla.citogenetica.model.type.StatusEnum;

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