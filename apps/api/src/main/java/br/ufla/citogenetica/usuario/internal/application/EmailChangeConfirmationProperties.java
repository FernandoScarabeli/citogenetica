package br.ufla.citogenetica.usuario.internal.application;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "api.security.email-change-confirmation")
public record EmailChangeConfirmationProperties(
        @Min(1) @Max(168) long expirationHours,
        @NotBlank String frontendUrl) {

    @AssertTrue(message = "A URL de confirmação da troca deve ser HTTP(S), conter host e não incluir fragmento")
    public boolean isFrontendUrlValid() {
        return EmailConfirmationUrlValidator.isValid(frontendUrl);
    }
}
