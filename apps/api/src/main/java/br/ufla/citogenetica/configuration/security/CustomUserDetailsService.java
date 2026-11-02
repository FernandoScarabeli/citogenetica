package br.ufla.citogenetica.configuration.security;

import br.ufla.citogenetica.exception.NotFoundException;
import br.ufla.citogenetica.model.usuario.Usuario;
import br.ufla.citogenetica.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public Usuario loadUserByUsername(String email) throws NotFoundException {
        Usuario usuario = this.usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(Usuario.class, email));
        return usuario;
    }
}