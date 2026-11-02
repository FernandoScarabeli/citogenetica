package br.ufla.citogenetica.model.crudExemplo.dto.response;

import br.ufla.citogenetica.model.type.ExemploEnum;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExemploResponseDTO(
        // Atributos da entidade Base
        Long id,
        Long version,
        LocalDateTime createdDate,
        LocalDateTime lastModifiedDate,

        // Atributos do model Exemplo
        ExemploEnum enumType,
        String descricao,
        BigDecimal valor,
        LocalDate dataExemplo,
        boolean ativo
) { }
