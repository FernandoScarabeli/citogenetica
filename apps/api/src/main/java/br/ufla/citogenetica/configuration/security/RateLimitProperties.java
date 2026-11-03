package br.ufla.citogenetica.configuration.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "api.security.rate-limit")
public record RateLimitProperties(
        @Min(1) @Max(1440) long windowMinutes,
        @Min(1) long cleanupIntervalMs,
        @NotNull @Valid Limits login,
        @NotNull @Valid Limits recoveryRequest,
        @NotNull @Valid Limits validate,
        @NotNull @Valid Limits reset) {

    public record Limits(@Min(1) int ip, @Min(1) int email) {
    }
}
