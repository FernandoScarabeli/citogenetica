package br.ufla.citogenetica.contagem.internal.web.dto.response;

import java.util.UUID;

public record ProtocoloResponseDTO(UUID id, String codigo, String nome, VersaoProtocoloResponseDTO versaoAtual) {
}
