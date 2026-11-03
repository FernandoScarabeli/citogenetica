package br.ufla.citogenetica.usuario.api.event;

/** Publicado pela composição da aplicação quando uma sessão autenticada é encerrada. */
public record SessaoEncerradaEvent(Long usuarioId, String sessionId) {
}
