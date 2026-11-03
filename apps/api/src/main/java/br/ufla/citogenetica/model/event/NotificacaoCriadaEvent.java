package br.ufla.citogenetica.model.event;

import br.ufla.citogenetica.model.notificacao.dto.NotificacaoResponseDTO;

/** Publicado dentro da transação que criou a notificação; o envio por SSE ocorre após o commit. */
public record NotificacaoCriadaEvent(Long destinatarioId, NotificacaoResponseDTO notificacao) {
}
