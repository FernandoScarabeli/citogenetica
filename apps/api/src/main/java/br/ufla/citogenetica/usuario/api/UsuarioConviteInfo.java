package br.ufla.citogenetica.usuario.api;

/** Estado mínimo da conta necessário para validar e notificar convites por e-mail. */
public record UsuarioConviteInfo(Long usuarioId, boolean ativo, boolean emailConfirmado) {
}
