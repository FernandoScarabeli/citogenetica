package br.ufla.citogenetica.configuration.security;

import br.ufla.citogenetica.model.usuario.Usuario;
import br.ufla.citogenetica.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
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
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var token = this.recoverToken(request);
        if (token != null && !token.isBlank()) {
            var subject = this.tokenService.getSubjectFromToken(token);
            if (subject.isPresent() && !subject.get().isBlank()) {
                Usuario user = this.usuarioRepository.findByEmail(subject.get()).orElse(null);
                if (user == null || !user.isEnabled()) {
                    SecurityContextHolder.clearContext();
                    this.authenticationEntryPoint.commence(
                            request,
                            response,
                            new BadCredentialsException("Falha na autenticação."));
                    return;
                }

                var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
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
