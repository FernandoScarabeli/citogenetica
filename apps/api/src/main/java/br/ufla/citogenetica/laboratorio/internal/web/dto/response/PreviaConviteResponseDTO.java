package br.ufla.citogenetica.laboratorio.internal.web.dto.response;

import java.util.List;

import br.ufla.citogenetica.laboratorio.internal.application.PreviaConvite;

public record PreviaConviteResponseDTO(
        String laboratorioNome,
        String convidadoPorNome,
        String emailMascarado,
        List<String> papeis,
        PreviaConvite.Estado estado,
        Boolean paraContaAtual) {

    public static PreviaConviteResponseDTO de(PreviaConvite previa) {
        return new PreviaConviteResponseDTO(previa.laboratorioNome(), previa.convidadoPorNome(),
                previa.emailMascarado(), previa.papeis(), previa.estado(), previa.paraContaAtual());
    }
}
