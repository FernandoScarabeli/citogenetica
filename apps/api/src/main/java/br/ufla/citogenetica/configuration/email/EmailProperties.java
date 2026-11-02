package br.ufla.citogenetica.configuration.email;

import br.ufla.citogenetica.configuration.secret.SecretValue;
import br.ufla.citogenetica.configuration.secret.ValidSecret;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        @NotBlank(message = "SPRING_MAIL_HOST é obrigatório.") String host,
        @Min(value = 1, message = "SPRING_MAIL_PORT deve estar entre 1 e 65535.")
        @Max(value = 65535, message = "SPRING_MAIL_PORT deve estar entre 1 e 65535.") int port,
        @NotBlank(message = "SPRING_MAIL_USERNAME é obrigatório.") String username,
        @ValidSecret(variable = "SPRING_MAIL_PASSWORD") SecretValue password,
        boolean auth,
        boolean starttlsEnable) {

    @AssertTrue(message = "SPRING_MAIL_STARTTLS_ENABLE deve ser true quando SPRING_MAIL_AUTH for true.")
    public boolean isAuthProtectedByStarttls() {
        return !auth || starttlsEnable;
    }
}
