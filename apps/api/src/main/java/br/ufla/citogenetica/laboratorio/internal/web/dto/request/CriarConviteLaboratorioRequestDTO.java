package br.ufla.citogenetica.laboratorio.internal.web.dto.request;

import java.util.Set;

import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CriarConviteLaboratorioRequestDTO(
        @NotBlank(message = "Informe o e-mail de destino.")
        @Email(message = "Informe um endereço de e-mail válido.")
        @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
        String email,
        @NotEmpty(message = "Informe ao menos o papel MEMBRO.")
        Set<PapelLaboratorio> papeis) {

    @AssertTrue(message = "O papel MEMBRO é obrigatório.")
    public boolean membroObrigatorio() {
        return papeis != null && papeis.contains(PapelLaboratorio.MEMBRO);
    }
}
