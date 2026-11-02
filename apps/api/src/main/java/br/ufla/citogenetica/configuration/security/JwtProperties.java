package br.ufla.citogenetica.configuration.security;

import br.ufla.citogenetica.configuration.secret.SecretValue;
import br.ufla.citogenetica.configuration.secret.ValidSecret;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "api.security.jwt")
public record JwtProperties(
        @ValidSecret(variable = "API_SECURITY_JWT_SECRET", minimumBytes = 32)
        SecretValue secret) {
}
