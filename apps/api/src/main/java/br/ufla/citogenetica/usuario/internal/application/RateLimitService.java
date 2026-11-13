package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.usuario.api.error.TooManyRequestsException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class RateLimitService {

    private static final String EMAIL_KEY_TYPE = "EMAIL";
    private static final String IP_KEY_TYPE = "IP";
    private static final String USER_KEY_TYPE = "USER";

    private final JdbcTemplate jdbcTemplate;
    private final RateLimitProperties properties;
    private final RecoveryHmacService hmacService;
    private final Clock clock;

    public RateLimitService(JdbcTemplate jdbcTemplate, RateLimitProperties properties,
                            RecoveryHmacService hmacService, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.hmacService = hmacService;
        this.clock = clock;
    }

    public void enforceIp(AuthRateLimitRoute route, String ipAddress) {
        String key = ipAddress == null || ipAddress.isBlank() ? "endereco-indisponivel" : ipAddress;
        check(route, IP_KEY_TYPE, hmacService.ipRateKey(key), ipLimit(route));
    }

    public void enforceEmail(AuthRateLimitRoute route, String normalizedEmail) {
        check(route, EMAIL_KEY_TYPE, hmacService.emailRateKey(normalizedEmail), limits(route).email());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enforceUser(AuthRateLimitRoute route, long userId) {
        check(route, USER_KEY_TYPE, hmacService.userRateKey(Long.toString(userId)), userLimit(route));
    }

    public int removeExpiredWindows() {
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(properties.windowMinutes()));
        return jdbcTemplate.update("DELETE FROM auth_rate_limit WHERE janela_inicio < ?", Timestamp.from(cutoff));
    }

    private void check(AuthRateLimitRoute route, String keyType, byte[] keyHash, int limit) {
        Instant now = clock.instant();
        long windowSeconds = Duration.ofMinutes(properties.windowMinutes()).toSeconds();
        long windowEpoch = Math.floorDiv(now.getEpochSecond(), windowSeconds) * windowSeconds;
        Instant windowStart = Instant.ofEpochSecond(windowEpoch);
        try {
            jdbcTemplate.queryForObject("""
                            INSERT INTO auth_rate_limit (rota, tipo_chave, chave_hmac, janela_inicio, tentativas)
                            VALUES (?, ?, ?, ?, 1)
                            ON CONFLICT (rota, tipo_chave, chave_hmac, janela_inicio)
                            DO UPDATE SET tentativas = auth_rate_limit.tentativas + 1
                            WHERE auth_rate_limit.tentativas < ?
                            RETURNING tentativas
                            """,
                    Integer.class, route.key(), keyType, keyHash, Timestamp.from(windowStart), limit);
        } catch (EmptyResultDataAccessException exception) {
            Duration wait = Duration.between(now, windowStart.plusSeconds(windowSeconds));
            long retryAfter = Math.max(1, wait.getSeconds() + (wait.getNano() > 0 ? 1 : 0));
            throw new TooManyRequestsException(retryAfter);
        }
    }

    private RateLimitProperties.Limits limits(AuthRateLimitRoute route) {
        return switch (route) {
            case LOGIN -> properties.login();
            case RECOVERY_REQUEST -> properties.recoveryRequest();
            case VALIDATE_RECOVERY -> properties.validate();
            case RESET_PASSWORD -> properties.reset();
            case REGISTER -> properties.register();
            case RESEND_CONFIRMATION -> properties.resendConfirmation();
            case EMAIL_CONFIRMATION -> properties.emailConfirmation();
            case CHANGE_EMAIL, CONFIRM_EMAIL_CHANGE -> throw new IllegalArgumentException(
                    "Esta rota não possui limite por e-mail.");
        };
    }

    private int ipLimit(AuthRateLimitRoute route) {
        return switch (route) {
            case CHANGE_EMAIL -> properties.changeEmail().ip();
            case CONFIRM_EMAIL_CHANGE -> properties.confirmEmailChange().ip();
            default -> limits(route).ip();
        };
    }

    private int userLimit(AuthRateLimitRoute route) {
        return switch (route) {
            case CHANGE_EMAIL -> properties.changeEmail().user();
            case CONFIRM_EMAIL_CHANGE -> properties.confirmEmailChange().user();
            default -> throw new IllegalArgumentException("Esta rota não possui limite por usuário.");
        };
    }
}
