package br.ufla.citogenetica.usuario.internal.domain.event;

public record EmailConfirmationRequestedEvent(String email, String confirmationUrl, long expirationHours) {

    @Override
    public String toString() {
        return "EmailConfirmationRequestedEvent[conteúdo protegido]";
    }
}
