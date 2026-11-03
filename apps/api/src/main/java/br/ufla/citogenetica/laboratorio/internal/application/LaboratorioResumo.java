package br.ufla.citogenetica.laboratorio.internal.application;

import java.util.Set;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.EstadoLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;

public record LaboratorioResumo(UUID id, String nome, EstadoLaboratorio estado,
                                Set<PapelLaboratorio> papeis, Long versao) {
}
