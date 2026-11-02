package br.ufla.citogenetica.model.notificacao.dto;

import br.ufla.citogenetica.model.type.TipoNotificacao;

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

