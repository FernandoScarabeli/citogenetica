package br.ufla.citogenetica.contagem.internal.application;

import java.util.List;
import java.util.UUID;

/** Pedido de edição de um registro, ainda sem validação; {@code status} chega como texto para o serviço responder 400. */
public record EdicaoRegistro(UUID laminaId, UUID registroId, Long versaoEsperada, UUID faseId, String status,
                             List<UUID> tipoIds, Integer quantidade) {
}
