package br.ufla.citogenetica.shared.secret;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class ValidSecretValidator implements ConstraintValidator<ValidSecret, SecretValue> {

    private int minimumBytes;
    private int maximumBytes;

    @Override
    public void initialize(ValidSecret constraintAnnotation) {
        this.minimumBytes = constraintAnnotation.minimumBytes();
        this.maximumBytes = constraintAnnotation.maximumBytes();
    }

    @Override
    public boolean isValid(SecretValue secret, ConstraintValidatorContext context) {
        if (secret == null || secret.reveal() == null || secret.reveal().isBlank()) {
            return false;
        }

        String normalized = secret.reveal().trim();
        int byteLength = normalized.getBytes(StandardCharsets.UTF_8).length;
        return !KnownSecretPlaceholder.isPlaceholder(normalized)
                && byteLength >= minimumBytes
                && byteLength <= maximumBytes;
    }
}
