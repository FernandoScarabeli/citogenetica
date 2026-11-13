package br.ufla.citogenetica.usuario.internal.security;

import br.ufla.citogenetica.usuario.internal.domain.EmailNormalizer;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public Usuario loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = EmailNormalizer.normalize(email);
        return this.usuarioRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas."));
    }
}
