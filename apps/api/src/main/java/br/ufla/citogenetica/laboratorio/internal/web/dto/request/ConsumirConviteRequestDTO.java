package br.ufla.citogenetica.laboratorio.internal.web.dto.request;

import jakarta.validation.constraints.Size;

/** Token malformado recebe a mesma resposta genérica que token inválido ou já consumido. */
public record ConsumirConviteRequestDTO(@Size(max = 128) String token) {
}
