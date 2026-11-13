package br.ufla.citogenetica.config.properties;

import br.ufla.citogenetica.shared.secret.SecretValue;
import br.ufla.citogenetica.shared.secret.ValidSecret;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.datasource")
public record DatasourceSecretProperties(
        @ValidSecret(variable = "SPRING_DATASOURCE_PASSWORD", minimumBytes = 32)
        SecretValue password) {
}
