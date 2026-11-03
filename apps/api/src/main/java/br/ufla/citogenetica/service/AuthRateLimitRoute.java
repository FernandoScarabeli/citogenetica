package br.ufla.citogenetica.service;

public enum AuthRateLimitRoute {
    LOGIN("login"),
    RECOVERY_REQUEST("recuperar-senha"),
    VALIDATE_RECOVERY("validar-codigo"),
    RESET_PASSWORD("redefinir-senha");

    private final String key;

    AuthRateLimitRoute(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
