package br.ufla.citogenetica.contagem.internal.application;

import java.time.Instant;
import java.util.List;

import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminas.LaminaExportada;

/** Lâminas (sem registros) com os cálculos oficiais de cada uma, na ordem da listagem. */
public record ExportacaoResumos(Instant exportadoEm, List<LaminaResumida> laminas) {

    public record LaminaResumida(LaminaExportada lamina, ResumoLamina resumo) {
    }
}
