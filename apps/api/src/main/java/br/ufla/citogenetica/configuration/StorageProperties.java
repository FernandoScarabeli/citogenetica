package br.ufla.citogenetica.configuration;

import br.ufla.citogenetica.configuration.secret.SecretValue;
import br.ufla.citogenetica.configuration.secret.ValidSecret;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        @NotBlank String internalEndpoint,
        @NotBlank String publicEndpoint,
        @NotBlank
        @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*-[0-9]+") String region,
        boolean pathStyle,
        @NotBlank
        @Size(min = 3, max = 63)
        @Pattern(regexp = "[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?") String bucket,
        @ValidSecret(variable = "STORAGE_ACCESS_KEY", minimumBytes = 3, maximumBytes = 128)
        SecretValue accessKey,
        @ValidSecret(
                variable = "STORAGE_SECRET_KEY",
                minimumBytes = 32,
                maximumBytes = 128,
                message = "STORAGE_SECRET_KEY deve ter entre 32 e 128 bytes e não pode ser um placeholder conhecido.")
        SecretValue secretKey,
        @Min(1) @Max(604800) long presignedUrlValiditySeconds) {

    private static final java.util.regex.Pattern ACCESS_KEY_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{2,127}");

    public String accessKeyValue() {
        return accessKey.reveal();
    }

    public String secretKeyValue() {
        return secretKey.reveal();
    }

    @AssertTrue(message = "STORAGE_ACCESS_KEY deve usar somente letras, números, ponto, hífen ou sublinhado.")
    public boolean isAccessKeyFormatValid() {
        return accessKey != null && ACCESS_KEY_PATTERN.matcher(accessKey.reveal()).matches();
    }

    @AssertTrue(message = "Os endpoints devem ser URLs HTTP(S) sem credenciais, caminho ou query string")
    public boolean isEndpointsValid() {
        return isValidEndpoint(internalEndpoint) && isValidEndpoint(publicEndpoint);
    }

    private static boolean isValidEndpoint(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String path = uri.getPath();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && (path == null || path.isEmpty() || "/".equals(path));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
