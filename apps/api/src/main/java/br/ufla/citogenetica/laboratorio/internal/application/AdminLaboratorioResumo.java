package br.ufla.citogenetica.laboratorio.internal.application;

import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.EstadoLaboratorio;

public record AdminLaboratorioResumo(
        UUID id,
        String nome,
        EstadoLaboratorio estado,
        long quantidadeMembrosAtivos,
        List<Administrador> administradores) {

    public AdminLaboratorioResumo {
        administradores = List.copyOf(administradores);
    }

    public record Administrador(String nome, String email) {
    }
}
