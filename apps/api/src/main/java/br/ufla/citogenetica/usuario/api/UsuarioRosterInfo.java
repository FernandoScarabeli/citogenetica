package br.ufla.citogenetica.usuario.api;

/** Dados mínimos de conta necessários para montar o roster de um laboratório. */
public record UsuarioRosterInfo(Long usuarioId, String nome, String email) {
}
