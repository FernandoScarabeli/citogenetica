package br.ufla.citogenetica.notificacao.internal.sse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Limites das conexões SSE. O limite por usuário impede que um cliente abra streams
 * sem fim e esgote threads e memória da API.
 */
@Validated
@ConfigurationProperties(prefix = "api.sse")
public record SseProperties(
        @Min(1) @Max(50) int maxConnectionsPerUser,
        @NotNull Duration timeout) {
}
