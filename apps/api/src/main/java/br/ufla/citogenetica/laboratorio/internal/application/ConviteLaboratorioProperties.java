package br.ufla.citogenetica.laboratorio.internal.application;

import br.ufla.citogenetica.shared.validation.FrontendConfirmationUrlValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "api.security.laboratory-invitations")
public record ConviteLaboratorioProperties(
        @Min(1) @Max(30) int expirationDays,
        @Min(1_000) long cleanupIntervalMs,
        @Min(1) int cleanupBatchSize,
        @NotBlank String frontendUrl,
        @NotNull @Valid RateLimit rateLimit) {

    @AssertTrue(message = "A URL do convite deve ser HTTPS ou HTTP local e não pode conter fragmento")
    public boolean isFrontendUrlValid() {
        return FrontendConfirmationUrlValidator.isValid(frontendUrl);
    }

    public record RateLimit(
            @Min(1) @Max(1440) long windowMinutes,
            @Min(1) int sendPerLaboratoryAuthor,
            @Min(1) int acceptPerIp,
            @Min(1) int acceptPerUser) {
    }
}
