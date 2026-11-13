package br.ufla.citogenetica.usuario.internal.application;

import java.util.Optional;

import br.ufla.citogenetica.usuario.api.UsuarioConviteInfo;
import br.ufla.citogenetica.usuario.api.UsuarioConviteQuery;
import br.ufla.citogenetica.usuario.internal.domain.EmailNormalizer;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioConviteQueryAdapter implements UsuarioConviteQuery {

    private final UsuarioRepository usuarioRepository;

    @Override
    public String normalizarEmail(String email) {
        return EmailNormalizer.normalize(email);
    }

    @Override
    public Optional<UsuarioConviteInfo> buscarPorEmailNormalizado(String emailNormalizado) {
        return usuarioRepository.findByEmail(emailNormalizado).map(UsuarioConviteQueryAdapter::paraInfo);
    }

    @Override
    public Optional<UsuarioConviteInfo> bloquearPorEmailNormalizado(String emailNormalizado) {
        return usuarioRepository.findByEmailForUpdate(emailNormalizado).map(UsuarioConviteQueryAdapter::paraInfo);
    }

    private static UsuarioConviteInfo paraInfo(Usuario usuario) {
        return new UsuarioConviteInfo(usuario.getId(),
                usuario.getStatus() == StatusEnum.ATIVO, usuario.getEmailConfirmadoEm() != null);
    }
}
