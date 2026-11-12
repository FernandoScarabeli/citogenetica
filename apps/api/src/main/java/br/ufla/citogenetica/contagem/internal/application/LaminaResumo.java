package br.ufla.citogenetica.contagem.internal.application;

import java.time.Instant;
import java.util.UUID;

public record LaminaResumo(UUID id, Long versao, Long donoUsuarioId, String donoNome, UUID laboratorioId,
                           UUID protocoloVersaoId, String nome, String especie, String tratamento,
                           String observacoes, Integer meta, long totalCelulas, Instant criadaEm, Instant atualizadaEm) {
}
