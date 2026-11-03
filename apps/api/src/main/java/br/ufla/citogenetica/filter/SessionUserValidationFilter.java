package br.ufla.citogenetica.filter;

import br.ufla.citogenetica.config.security.ApiAuthenticationEntryPoint;
import br.ufla.citogenetica.model.usuario.Usuario;
import br.ufla.citogenetica.model.usuario.UsuarioPrincipal;
import br.ufla.citogenetica.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SessionUserValidationFilter extends OncePerRequestFilter {

    private final UsuarioRepository usuarioRepository;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;
    private final SecurityContextRepository securityContextRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UsuarioPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Usuario> usuarioEncontrado = usuarioRepository.findById(principal.id());
        if (usuarioEncontrado.isEmpty() || !usuarioEncontrado.get().isEnabled()) {
            SecurityContextHolder.clearContext();
            if (request.getSession(false) != null) {
                try {
                    request.getSession(false).invalidate();
                } catch (IllegalStateException ignored) {
                    // Uma revogação concorrente já encerrou esta sessão.
                }
            }
            authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("Falha na autenticação."));
            return;
        }

        UsuarioPrincipal atualizado = UsuarioPrincipal.de(usuarioEncontrado.get());
        if (!atualizado.equals(principal)) {
            UsernamePasswordAuthenticationToken autenticacaoAtualizada =
                    new UsernamePasswordAuthenticationToken(atualizado, null, atualizado.getAuthorities());
            autenticacaoAtualizada.setDetails(authentication.getDetails());
            SecurityContext contexto = SecurityContextHolder.getContext();
            contexto.setAuthentication(autenticacaoAtualizada);
            securityContextRepository.saveContext(contexto, request, response);
        }
        filterChain.doFilter(request, response);
    }
}
