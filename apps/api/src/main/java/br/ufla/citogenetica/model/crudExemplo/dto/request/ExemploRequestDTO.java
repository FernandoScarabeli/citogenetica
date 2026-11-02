package br.ufla.citogenetica.model.crudExemplo.dto.request;

import br.ufla.citogenetica.model.type.ExemploEnum;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExemploRequestDTO(
        @NotNull(message = "O tipo do enum não pode ser nulo")
        ExemploEnum enumType,

        @NotNull(message = "A descrição não pode ser nula")
        @Size(min = 3, max = 255, message = "A descrição deve ter entre {min} e {max} caracteres")
        String descricao,

        @NotNull(message = "O valor não pode ser nulo")
        @PositiveOrZero(message = "O valor deve ser positivo ou zero")
        BigDecimal valor,

        @NotNull(message = "A data não pode ser nula")
        @FutureOrPresent(message = "A data do exemplo não pode ser no passado")
        LocalDate dataExemplo,

        @NotNull(message = "O campo 'ativo' não pode ser nulo")
        Boolean ativo
) {}
