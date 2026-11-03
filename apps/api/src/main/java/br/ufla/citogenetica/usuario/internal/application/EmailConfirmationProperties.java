package br.ufla.citogenetica.usuario.internal.application;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;


@Validated
@ConfigurationProperties(prefix = "api.security.email-confirmation")
public record EmailConfirmationProperties(
        @Min(1) @Max(168) long expirationHours,
        @Min(1) @Max(365) int pendingRetentionDays,
        @Min(1_000) long cleanupIntervalMs,
        @Min(1) int cleanupBatchSize,
        @NotBlank String frontendUrl) {

    @AssertTrue(message = "A URL de confirmação deve ser HTTP(S), conter host e não pode incluir fragmento")
    public boolean isFrontendUrlValid() {
        return EmailConfirmationUrlValidator.isValid(frontendUrl);
    }
}
