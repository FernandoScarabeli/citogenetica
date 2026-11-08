package br.ufla.citogenetica.contagem.internal.application;

import java.util.List;
import java.util.UUID;

/** Pedido de desfazer, ainda sem validação; {@code status} chega como texto para o serviço responder 400. */
public record PedidoDesfazer(UUID id, UUID laminaId, UUID faseId, String status, List<UUID> tipoIds,
                             Integer quantidade) {
}
