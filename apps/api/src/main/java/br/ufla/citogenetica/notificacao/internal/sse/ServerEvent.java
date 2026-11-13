package br.ufla.citogenetica.notificacao.internal.sse;

import br.ufla.citogenetica.notificacao.internal.web.dto.response.NotificacaoResponseDTO;

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

