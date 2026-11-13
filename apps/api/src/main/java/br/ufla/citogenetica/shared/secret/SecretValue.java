package br.ufla.citogenetica.shared.secret;

import java.util.Objects;

/** Segredo carregada da configuração; o {@code toString} nunca expõe o valor. */
public final class SecretValue {

    private final String value;

    public SecretValue(String value) {
        this.value = value;
    }

    public String reveal() {
        return value;
    }

    @Override
    public String toString() {
        return "[REDACTED]";
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof SecretValue that
                && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
