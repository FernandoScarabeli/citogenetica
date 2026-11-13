package br.ufla.citogenetica.laboratorio.api.event;

import java.util.Set;
import java.util.UUID;

/** Evento pós-commit para notificar contas existentes; nunca contém token de ação. */
public record ConviteLaboratorioCriadoEvent(
        UUID conviteId,
        UUID laboratorioId,
        Long convidadoPorUsuarioId,
        String laboratorioNome,
        String emailDestino,
        Set<String> papeis) {

    public ConviteLaboratorioCriadoEvent {
        papeis = Set.copyOf(papeis);
    }

    @Override
    public String toString() {
        return "ConviteLaboratorioCriadoEvent[conviteId=" + conviteId
                + ", laboratorioId=" + laboratorioId
                + ", convidadoPorUsuarioId=" + convidadoPorUsuarioId + "]";
    }
}
