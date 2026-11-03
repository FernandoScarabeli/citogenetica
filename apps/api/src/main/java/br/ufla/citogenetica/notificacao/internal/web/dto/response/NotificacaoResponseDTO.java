package br.ufla.citogenetica.notificacao.internal.web.dto.response;

import br.ufla.citogenetica.notificacao.internal.domain.TipoNotificacao;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificacaoResponseDTO(
        Long id,
        TipoNotificacao tipo,
        String titulo,
        String mensagem,
        Map<String, Object> dados,
        Boolean lida,
        LocalDateTime dataCriacao
) {}

