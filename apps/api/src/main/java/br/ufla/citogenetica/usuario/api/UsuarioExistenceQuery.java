package br.ufla.citogenetica.usuario.api;

/** Consulta pública mínima para módulos que precisam validar a referência a um usuário. */
public interface UsuarioExistenceQuery {

    boolean existsById(Long usuarioId);
}
