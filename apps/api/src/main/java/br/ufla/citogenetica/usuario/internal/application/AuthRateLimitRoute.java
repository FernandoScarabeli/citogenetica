package br.ufla.citogenetica.usuario.internal.application;

public enum AuthRateLimitRoute {
    LOGIN("login"),
    RECOVERY_REQUEST("recuperar-senha"),
    VALIDATE_RECOVERY("validar-codigo"),
    RESET_PASSWORD("redefinir-senha"),
    REGISTER("register"),
    RESEND_CONFIRMATION("reenviar-confirmacao"),
    EMAIL_CONFIRMATION("confirmar-email");

    private final String key;

    AuthRateLimitRoute(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
