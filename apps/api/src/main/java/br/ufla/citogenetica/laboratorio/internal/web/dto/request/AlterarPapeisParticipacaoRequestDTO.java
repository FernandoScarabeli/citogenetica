package br.ufla.citogenetica.laboratorio.internal.web.dto.request;

import java.util.Set;

import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record AlterarPapeisParticipacaoRequestDTO(
        @NotNull(message = "Informe a versão esperada da participação.") Long versaoEsperada,
        @NotEmpty(message = "Informe ao menos um papel.") Set<PapelLaboratorio> papeis) {

    @AssertTrue(message = "Toda participação ativa deve manter o papel MEMBRO.")
    public boolean membroObrigatorio() {
        return papeis != null && papeis.contains(PapelLaboratorio.MEMBRO);
    }
}
