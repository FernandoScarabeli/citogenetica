package br.ufla.citogenetica.contagem.internal.web.dto.response;

import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.application.ResumoLamina;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cálculos oficiais da lâmina, somando a quantidade dos registros não excluídos")
public record ResumoLaminaResponseDTO(
        UUID laminaId,
        @Schema(description = "Total de células contadas")
        long total,
        @Schema(description = "Total de células anormais")
        long anormais,
        @Schema(description = "Índice mitótico: células em fases que contam como divisão ÷ total × 100; 0 sem registros")
        double indiceMitotico,
        @Schema(description = "Todas as fases da versão do protocolo, na ordem do protocolo, mesmo sem registros")
        List<Fase> fases) {

    public static ResumoLaminaResponseDTO de(ResumoLamina resumo) {
        return new ResumoLaminaResponseDTO(resumo.laminaId(), resumo.total(), resumo.anormais(),
                resumo.indiceMitotico(), resumo.fases().stream().map(Fase::de).toList());
    }

    public record Fase(
            UUID faseId,
            String nome,
            int ordem,
            long normais,
            long anormais,
            long total,
            @Schema(description = "Anormais ÷ total da fase × 100; 0 sem registros")
            double percentualAnormais,
            @Schema(description = "Só tipos com contagem maior que zero. Um registro com vários tipos conta uma vez "
                    + "em cada um, então a soma dos tipos pode passar do número de anormais")
            List<Tipo> tipos) {

        static Fase de(ResumoLamina.Fase fase) {
            return new Fase(fase.faseId(), fase.nome(), fase.ordem(), fase.normais(), fase.anormais(), fase.total(),
                    fase.percentualAnormais(), fase.tipos().stream().map(Tipo::de).toList());
        }
    }

    public record Tipo(
            UUID tipoId,
            String nome,
            @Schema(description = "Células com este tipo na fase")
            long contagem,
            @Schema(description = "Contagem ÷ total da fase × 100")
            double percentual) {

        static Tipo de(ResumoLamina.Tipo tipo) {
            return new Tipo(tipo.tipoId(), tipo.nome(), tipo.contagem(), tipo.percentual());
        }
    }
}
