package br.ufla.citogenetica.contagem.internal.web.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.application.RegistroHistorico;
import br.ufla.citogenetica.contagem.internal.application.RegistroResumo;
import br.ufla.citogenetica.contagem.internal.application.TipoNomeado;
import br.ufla.citogenetica.contagem.internal.domain.StatusRegistro;
import io.swagger.v3.oas.annotations.media.Schema;

/** O registro atual mais os tipos com nome, porque o nome de um tipo personalizado é do dono da lâmina. */
public record RegistroHistoricoResponseDTO(
        UUID id,
        @Schema(description = "Versão atual do registro")
        Long versao,
        UUID laminaId,
        UUID faseId,
        StatusRegistro status,
        @Schema(description = "Tipos de anormalidade em ordem crescente do UUID; vazio quando NORMAL")
        List<UUID> tipoIds,
        @Schema(description = "Número de células do lote")
        int quantidade,
        @Schema(description = "Hora oficial, definida pelo servidor")
        Instant registradoEm,
        @Schema(description = "Hora informada pelo aparelho, só informativa", nullable = true)
        Instant horaAparelho,
        @Schema(description = "Fuso informado pelo aparelho, só informativo", nullable = true)
        String fusoAparelho,
        @Schema(description = "Os mesmos tipos de tipoIds, com o nome")
        List<TipoDoRegistroResponseDTO> tipos) {

    public record TipoDoRegistroResponseDTO(UUID id, String nome) {

        static TipoDoRegistroResponseDTO de(TipoNomeado tipo) {
            return new TipoDoRegistroResponseDTO(tipo.id(), tipo.nome());
        }
    }

    public static RegistroHistoricoResponseDTO de(RegistroHistorico historico) {
        RegistroResumo registro = historico.registro();
        return new RegistroHistoricoResponseDTO(registro.id(), registro.versao(), registro.laminaId(),
                registro.faseId(), registro.status(), registro.tipoIds(), registro.quantidade(),
                registro.registradoEm(), registro.horaAparelho(), registro.fusoAparelho(),
                historico.tipos().stream().map(TipoDoRegistroResponseDTO::de).toList());
    }
}
