package br.ufla.citogenetica.usuario.api;

import java.util.Optional;

/** Porta pública para normalizar e consultar destinatários sem expor entidades de usuário. */
public interface UsuarioConviteQuery {

    String normalizarEmail(String email);

    Optional<UsuarioConviteInfo> buscarPorEmailNormalizado(String emailNormalizado);

    Optional<UsuarioConviteInfo> bloquearPorEmailNormalizado(String emailNormalizado);
}
