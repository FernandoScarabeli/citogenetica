package br.ufla.citogenetica.contagem.internal.application;

import java.util.List;
import java.util.UUID;

/**
 * Pedido de criação de um registro, ainda sem validação. {@code status}, {@code horaAparelho} e
 * {@code fusoAparelho} chegam como texto para que o serviço responda 400 com mensagem própria.
 */
public record NovoRegistro(UUID id, UUID laminaId, UUID faseId, String status, List<UUID> tipoIds,
                           Integer quantidade, String horaAparelho, String fusoAparelho) {
}
