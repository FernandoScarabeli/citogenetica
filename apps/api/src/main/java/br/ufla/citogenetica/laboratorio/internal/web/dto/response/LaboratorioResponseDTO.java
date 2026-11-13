package br.ufla.citogenetica.laboratorio.internal.web.dto.response;

import java.util.Set;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.EstadoLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;

public record LaboratorioResponseDTO(UUID id, String nome, EstadoLaboratorio estado,
                                    Set<PapelLaboratorio> papeis, Long versao) {
}
