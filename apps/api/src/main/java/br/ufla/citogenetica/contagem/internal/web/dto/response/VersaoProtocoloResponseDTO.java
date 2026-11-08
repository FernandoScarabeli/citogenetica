package br.ufla.citogenetica.contagem.internal.web.dto.response;

import java.util.List;
import java.util.UUID;

public record VersaoProtocoloResponseDTO(UUID id, int numero, List<FaseProtocoloResponseDTO> fases) {
}
