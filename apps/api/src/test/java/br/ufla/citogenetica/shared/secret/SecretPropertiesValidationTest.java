package br.ufla.citogenetica.shared.secret;

import br.ufla.citogenetica.config.properties.DatasourceSecretProperties;
import br.ufla.citogenetica.config.secret.SecretValueConverterConfiguration;
import br.ufla.citogenetica.infra.storage.StorageProperties;
import br.ufla.citogenetica.infra.email.EmailProperties;
import br.ufla.citogenetica.usuario.internal.application.RecoveryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecretPropertiesValidationTest {

    private static final String RECOVERY_SECRET = "2fa5a79b3d218c4f".repeat(4);
    private static final String STORAGE_SECRET = "c1b07af428cc48a89c3466e50741be70";
    private static final String DATABASE_SECRET = "fa61c0461cd44960b372e05d17c83340";
    private static final String MAIL_SECRET = "c71dd64694764c0d9a4d365e9fdc5171";

    @Test
    void startsWithValidSecretSettings() {
        context(validProperties()).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rejectsMissingShortAndPlaceholderRecoveryHmacSecret() {
        Map<String, String> missing = validProperties();
        missing.remove("api.security.recovery.hmac-secret");
        assertRejected(missing, "API_SECURITY_RECOVERY_HMAC_SECRET", "");

        Map<String, String> shortValue = validProperties();
        shortValue.put("api.security.recovery.hmac-secret", "curto-demais");
        assertRejected(shortValue, "API_SECURITY_RECOVERY_HMAC_SECRET", "curto-demais");

        Map<String, String> placeholder = validProperties();
        placeholder.put("api.security.recovery.hmac-secret", "troque-isto");
        assertRejected(placeholder, "API_SECURITY_RECOVERY_HMAC_SECRET", "troque-isto");
    }

    @Test
    void rejectsMissingShortAndPlaceholderStorageSecrets() {
        Map<String, String> missing = validProperties();
        missing.remove("storage.secret-key");
        assertRejected(missing, "STORAGE_SECRET_KEY", "");

        Map<String, String> shortValue = validProperties();
        shortValue.put("storage.secret-key", "short-store-value");
        assertRejected(shortValue, "STORAGE_SECRET_KEY", "short-store-value");

        Map<String, String> placeholder = validProperties();
        placeholder.put("storage.secret-key", "troque-isto-store");
        assertRejected(placeholder, "STORAGE_SECRET_KEY", "troque-isto-store");
    }

    @Test
    void rejectsMissingShortAndPlaceholderDatasourcePasswords() {
        Map<String, String> missing = validProperties();
        missing.remove("app.datasource.password");
        assertRejected(missing, "SPRING_DATASOURCE_PASSWORD", "");

        Map<String, String> shortValue = validProperties();
        shortValue.put("app.datasource.password", "short-db-value");
        assertRejected(shortValue, "SPRING_DATASOURCE_PASSWORD", "short-db-value");

        Map<String, String> placeholder = validProperties();
        placeholder.put("app.datasource.password", "  POSTGRES  ");
        assertRejected(placeholder, "SPRING_DATASOURCE_PASSWORD", "POSTGRES");
    }

    @Test
    void rejectsMissingAndPlaceholderMailPassword() {
        Map<String, String> missing = validProperties();
        missing.remove("app.email.password");
        assertRejected(missing, "SPRING_MAIL_PASSWORD", "");

        Map<String, String> placeholder = validProperties();
        placeholder.put("app.email.password", "sua-senha-de-exemplo");
        assertRejected(placeholder, "SPRING_MAIL_PASSWORD", "sua-senha-de-exemplo");
    }

    @Test
    void rejectsSmtpAuthenticationWithoutStarttls() {
        Map<String, String> properties = validProperties();
        properties.put("app.email.auth", "true");
        properties.put("app.email.starttls-enable", "false");

        context(properties).run(result -> {
            assertThat(result).hasFailed();
            assertThat(messages(result.getStartupFailure()))
                    .contains("SPRING_MAIL_STARTTLS_ENABLE")
                    .doesNotContain(MAIL_SECRET);
        });
    }

    private static void assertRejected(Map<String, String> properties, String variable, String rejectedValue) {
        context(properties).run(result -> {
            assertThat(result).hasFailed();
            String messages = messages(result.getStartupFailure());
            assertThat(messages).contains(variable);
            if (!rejectedValue.isEmpty()) {
                assertThat(messages).doesNotContain(rejectedValue);
            }
        });
    }

    private static ApplicationContextRunner context(Map<String, String> properties) {
        String[] propertyValues = properties.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);

        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        ValidationAutoConfiguration.class))
                .withUserConfiguration(TestPropertiesConfiguration.class)
                .withInitializer(context -> context.getEnvironment().getPropertySources().remove("systemEnvironment"))
                .withPropertyValues(propertyValues);
    }

    private static Map<String, String> validProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("api.security.recovery.hmac-secret", RECOVERY_SECRET);
        properties.put("api.security.recovery.max-attempts", "5");
        properties.put("api.security.recovery.email-executor-core-size", "1");
        properties.put("api.security.recovery.email-executor-max-size", "2");
        properties.put("api.security.recovery.email-executor-queue-capacity", "50");
        properties.put("app.datasource.password", DATABASE_SECRET);
        properties.put("app.email.host", "mailpit");
        properties.put("app.email.port", "1025");
        properties.put("app.email.username", "desenvolvimento");
        properties.put("app.email.password", MAIL_SECRET);
        properties.put("app.email.auth", "false");
        properties.put("app.email.starttls-enable", "false");
        properties.put("storage.internal-endpoint", "http://rustfs:9000");
        properties.put("storage.region", "us-east-1");
        properties.put("storage.path-style", "true");
        properties.put("storage.bucket", "citogenetica");
        properties.put("storage.access-key", "a8398767b0d546459aa33edbcdecdff1");
        properties.put("storage.secret-key", STORAGE_SECRET);
        return properties;
    }

    private static String messages(Throwable failure) {
        StringBuilder result = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            result.append(current.getMessage()).append('\n');
        }
        return result.toString();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            RecoveryProperties.class,
            DatasourceSecretProperties.class,
            EmailProperties.class,
            StorageProperties.class
    })
    @Import(SecretValueConverterConfiguration.class)
    static class TestPropertiesConfiguration {
    }
}
