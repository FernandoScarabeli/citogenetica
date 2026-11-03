package br.ufla.citogenetica.usuario.internal.domain.event;

public record EmailAddressChangedEvent(String previousEmail) {

    @Override
    public String toString() {
        return "EmailAddressChangedEvent[conteúdo protegido]";
    }
}
