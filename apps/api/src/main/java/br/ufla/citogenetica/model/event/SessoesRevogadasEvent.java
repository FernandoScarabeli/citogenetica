package br.ufla.citogenetica.model.event;

/**
 * Publicado quando as sessões de um usuário são revogadas. {@code sessionIdMantido} é a sessão
 * que continua válida (troca da própria senha) ou {@code null} quando todas caem.
 */
public record SessoesRevogadasEvent(Long usuarioId, String sessionIdMantido) {
}
