package br.ufla.citogenetica.configuration;

import br.ufla.citogenetica.configuration.secret.SecretValue;
import br.ufla.citogenetica.configuration.secret.ValidSecret;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.datasource")
public record DatasourceSecretProperties(
        @ValidSecret(variable = "SPRING_DATASOURCE_PASSWORD", minimumBytes = 32)
        SecretValue password) {
}
