package br.ufla.citogenetica.shared.secret;

import java.util.Locale;
import java.util.Set;

/** Política central de placeholders conhecidos, compartilhada por todas as credenciais validadas. */
public final class KnownSecretPlaceholder {

    private static final Set<String> KNOWN_VALUES = Set.of(
            "postgres",
            "password",
            "secret",
            "changeme",
            "change-me",
            "change_me",
            "replace-me",
            "replace_me",
            "placeholder",
            "todo");

    private KnownSecretPlaceholder() {
    }

    public static boolean isPlaceholder(String value) {
        if (value == null) {
            return false;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return KNOWN_VALUES.contains(normalized)
                || normalized.startsWith("troque-isto")
                || normalized.startsWith("seu-")
                || normalized.startsWith("sua-")
                || normalized.startsWith("your-")
                || normalized.startsWith("change-me-")
                || normalized.startsWith("replace-me-")
                || normalized.startsWith("example-");
    }
}
