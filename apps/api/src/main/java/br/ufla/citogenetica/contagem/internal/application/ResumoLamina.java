package br.ufla.citogenetica.contagem.internal.application;

import java.util.List;
import java.util.UUID;

/** Cálculos oficiais de uma lâmina. Contagens em {@code long}; percentuais em {@code double}, sem arredondar. */
public record ResumoLamina(UUID laminaId, long total, long anormais, double indiceMitotico, List<Fase> fases) {

    public record Fase(UUID faseId, String nome, int ordem, long normais, long anormais, long total,
                       double percentualAnormais, List<Tipo> tipos) {
    }

    public record Tipo(UUID tipoId, String nome, long contagem, double percentual) {
    }
}
