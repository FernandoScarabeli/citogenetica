package br.ufla.citogenetica.laboratorio.api.event;

import java.util.UUID;

/** Evento pós-commit sem token para criação de notificação a contas existentes. */
public record ConviteOrientacaoCriadoEvent(
        UUID conviteId,
        UUID laboratorioId,
        Long orientadorUsuarioId,
        String laboratorioNome,
        String emailDestino) {

    @Override
    public String toString() {
        return "ConviteOrientacaoCriadoEvent[conviteId=" + conviteId
                + ", laboratorioId=" + laboratorioId
                + ", orientadorUsuarioId=" + orientadorUsuarioId + "]";
    }
}
