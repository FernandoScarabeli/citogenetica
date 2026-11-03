package br.ufla.citogenetica.laboratorio.internal.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SalvarLaboratorioRequestDTO(
        @NotBlank(message = "Informe o nome do laboratório.")
        @Size(max = 120, message = "O nome do laboratório deve ter no máximo 120 caracteres.")
        String nome) {
}
