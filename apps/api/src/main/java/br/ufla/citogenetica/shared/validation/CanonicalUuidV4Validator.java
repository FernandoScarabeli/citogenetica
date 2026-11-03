package br.ufla.citogenetica.shared.validation;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Valida somente a representação canônica em minúsculas de UUIDv4 RFC 4122/9562. */
public class CanonicalUuidV4Validator implements ConstraintValidator<CanonicalUuidV4, String> {

    private static final Pattern FORMATO_CANONICO_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (!FORMATO_CANONICO_V4.matcher(value).matches()) {
            return false;
        }

        UUID uuid = UUID.fromString(value);
        return uuid.version() == 4 && uuid.variant() == 2 && uuid.toString().equals(value);
    }
}
