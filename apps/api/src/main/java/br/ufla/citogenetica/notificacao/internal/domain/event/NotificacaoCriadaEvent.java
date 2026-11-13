package br.ufla.citogenetica.notificacao.internal.domain.event;

import br.ufla.citogenetica.notificacao.internal.web.dto.response.NotificacaoResponseDTO;

/** Publicado dentro da transação que criou a notificação; o envio por SSE ocorre após o commit. */
public record NotificacaoCriadaEvent(Long destinatarioId, NotificacaoResponseDTO notificacao) {
}
