package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.shared.secret.SecretValue;
import br.ufla.citogenetica.shared.secret.ValidSecret;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "api.security.recovery")
public record RecoveryProperties(
        @ValidSecret(variable = "API_SECURITY_RECOVERY_HMAC_SECRET", minimumBytes = 32)
        SecretValue hmacSecret,
        @Min(1) int maxAttempts,
        @Min(1) int emailExecutorCoreSize,
        @Min(1) int emailExecutorMaxSize,
        @Min(1) int emailExecutorQueueCapacity) {

    @AssertTrue(message = "O tamanho máximo do executor de e-mail deve ser maior ou igual ao tamanho principal.")
    public boolean isEmailExecutorPoolValid() {
        return emailExecutorMaxSize >= emailExecutorCoreSize;
    }
}
