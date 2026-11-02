package br.ufla.citogenetica.model.event;

import br.ufla.citogenetica.model.notificacao.dto.NotificacaoResponseDTO;

import java.time.LocalDateTime;

public record ServerEvent(
        String type,
        NotificacaoResponseDTO payload,
        LocalDateTime timestamp
) {
    public ServerEvent(NotificacaoResponseDTO payload) {
        this(payload.tipo().name(), payload, LocalDateTime.now());
    }
}

