package br.ufla.citogenetica.model.usuario;

public record RecoveryEmailRequestedEvent(String email, String code, long expirationMinutes) {

    @Override
    public String toString() {
        return "RecoveryEmailRequestedEvent[conteúdo protegido]";
    }
}
