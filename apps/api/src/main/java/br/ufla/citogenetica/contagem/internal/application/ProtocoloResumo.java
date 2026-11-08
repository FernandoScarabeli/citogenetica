package br.ufla.citogenetica.contagem.internal.application;

import java.util.UUID;

public record ProtocoloResumo(UUID id, String codigo, String nome, VersaoProtocoloResumo versaoAtual) {
}
