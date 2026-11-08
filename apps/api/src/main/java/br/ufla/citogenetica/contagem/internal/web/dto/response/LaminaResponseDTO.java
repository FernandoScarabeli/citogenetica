package br.ufla.citogenetica.contagem.internal.web.dto.response;

import java.time.Instant;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.application.LaminaResumo;
import io.swagger.v3.oas.annotations.media.Schema;

public record LaminaResponseDTO(
        UUID id,
        @Schema(description = "Versão atual, a enviar como versaoEsperada em PATCH e DELETE")
        Long versao,
        Long donoUsuarioId,
        String donoNome,
        @Schema(description = "Nulo quando a lâmina é avulsa", nullable = true)
        UUID laboratorioId,
        UUID protocoloVersaoId,
        String nome,
        String especie,
        String tratamento,
        String observacoes,
        @Schema(description = "Meta de células; nula quando não há meta", nullable = true)
        Integer meta,
        Instant criadaEm,
        Instant atualizadaEm) {

    public static LaminaResponseDTO de(LaminaResumo resumo) {
        return new LaminaResponseDTO(resumo.id(), resumo.versao(), resumo.donoUsuarioId(), resumo.donoNome(),
                resumo.laboratorioId(), resumo.protocoloVersaoId(), resumo.nome(), resumo.especie(),
                resumo.tratamento(), resumo.observacoes(), resumo.meta(), resumo.criadaEm(), resumo.atualizadaEm());
    }
}
