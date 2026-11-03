package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.usuario.api.UsuarioExistenceQuery;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Implementa a consulta pública usando a persistência interna de usuários. */
@Service
@RequiredArgsConstructor
public class UsuarioExistenceQueryAdapter implements UsuarioExistenceQuery {

    private final UsuarioRepository usuarioRepository;

    @Override
    public boolean existsById(Long usuarioId) {
        return usuarioRepository.existsById(usuarioId);
    }
}
