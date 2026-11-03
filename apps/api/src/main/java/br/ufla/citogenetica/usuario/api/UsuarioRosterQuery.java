package br.ufla.citogenetica.usuario.api;

import java.util.Collection;
import java.util.List;

/** Consulta pública, sem entidades JPA, para exibir participantes de laboratório. */
public interface UsuarioRosterQuery {

    List<UsuarioRosterInfo> buscarPorIds(Collection<Long> usuarioIds);
}
