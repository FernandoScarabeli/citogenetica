package br.ufla.citogenetica.usuario.internal.domain.event;

public record EmailChangeConfirmationRequestedEvent(String email, String confirmationUrl, long expirationHours) {

    @Override
    public String toString() {
        return "EmailChangeConfirmationRequestedEvent[conteúdo protegido]";
    }
}
