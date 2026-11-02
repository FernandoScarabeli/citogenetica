package br.ufla.citogenetica.configuration.security;

import br.ufla.citogenetica.exception.NotFoundException;
import br.ufla.citogenetica.model.usuario.Usuario;
import br.ufla.citogenetica.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Component
public class SecurityFilter extends OncePerRequestFilter {

    private final JWTConfig tokenService;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var token = this.recoverToken(request);
        if (token != null && !token.isBlank()) {
            this.tokenService.getSubjectFromToken(token)
                    .filter(login -> !login.isBlank())
                    .ifPresent(login -> {
                        Usuario user = this.usuarioRepository.findByEmail(login)
                                .orElseThrow(() -> new NotFoundException(Usuario.class, login));

                        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {

        var authHeader = request.getHeader("Authorization");
        if (authHeader != null && !authHeader.isBlank()) {
            return authHeader.replace("Bearer ", "");
        }

        var tokenParam = request.getParameter("token");
        if (tokenParam != null && !tokenParam.isBlank()) {
            return tokenParam;
        }

        return null;
    }
}
