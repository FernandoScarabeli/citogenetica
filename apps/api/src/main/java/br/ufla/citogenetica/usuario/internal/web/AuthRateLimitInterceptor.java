package br.ufla.citogenetica.usuario.internal.web;

import br.ufla.citogenetica.usuario.internal.application.AuthRateLimitRoute;
import br.ufla.citogenetica.usuario.internal.application.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    public AuthRateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        AuthRateLimitRoute route = routeFor(request.getRequestURI());
        if (route == null) {
            return true;
        }

        rateLimitService.enforceIp(route, request.getRemoteAddr());
        return true;
    }

    private AuthRateLimitRoute routeFor(String path) {
        return switch (path) {
            case "/api/v1/auth/login" -> AuthRateLimitRoute.LOGIN;
            case "/api/v1/auth/recuperar-senha" -> AuthRateLimitRoute.RECOVERY_REQUEST;
            case "/api/v1/auth/validar-codigo" -> AuthRateLimitRoute.VALIDATE_RECOVERY;
            case "/api/v1/auth/redefinir-senha" -> AuthRateLimitRoute.RESET_PASSWORD;
            case "/api/v1/auth/register" -> AuthRateLimitRoute.REGISTER;
            case "/api/v1/auth/reenviar-confirmacao" -> AuthRateLimitRoute.RESEND_CONFIRMATION;
            case "/api/v1/auth/confirmar-email" -> AuthRateLimitRoute.EMAIL_CONFIRMATION;
            case "/api/v1/usuarios/me/email" -> AuthRateLimitRoute.CHANGE_EMAIL;
            case "/api/v1/auth/confirmar-troca-email" -> AuthRateLimitRoute.CONFIRM_EMAIL_CHANGE;
            default -> null;
        };
    }
}
