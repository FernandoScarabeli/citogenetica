package br.ufla.citogenetica.usuario.internal.domain.event;

public record RecoveryEmailRequestedEvent(String email, String code, long expirationMinutes) {

    @Override
    public String toString() {
        return "RecoveryEmailRequestedEvent[conteúdo protegido]";
    }
}
