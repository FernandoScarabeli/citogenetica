package br.ufla.citogenetica.contagem.internal.application;

import java.util.UUID;

/** Dados de criação de uma lâmina, ainda sem normalização de texto. */
public record NovaLamina(UUID id, UUID protocoloVersaoId, UUID laboratorioId, String nome, String especie,
                         String tratamento, String observacoes, Integer meta) {
}
