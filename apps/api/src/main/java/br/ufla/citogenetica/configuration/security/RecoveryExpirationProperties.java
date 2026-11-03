package br.ufla.citogenetica.configuration.security;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "api.security.token")
public record RecoveryExpirationProperties(@Min(1) long recoveryExpirationMinutes) {
}
