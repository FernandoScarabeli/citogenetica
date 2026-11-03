package br.ufla.citogenetica.usuario.internal.application;

import java.util.Collection;
import java.util.List;

import br.ufla.citogenetica.usuario.api.UsuarioRosterInfo;
import br.ufla.citogenetica.usuario.api.UsuarioRosterQuery;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioRosterQueryAdapter implements UsuarioRosterQuery {

    private final UsuarioRepository usuarioRepository;

    @Override
    public List<UsuarioRosterInfo> buscarPorIds(Collection<Long> usuarioIds) {
        if (usuarioIds.isEmpty()) {
            return List.of();
        }
        return usuarioRepository.buscarDadosRoster(usuarioIds);
    }
}
