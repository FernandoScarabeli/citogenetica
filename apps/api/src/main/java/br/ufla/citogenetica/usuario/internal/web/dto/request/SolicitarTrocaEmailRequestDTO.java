package br.ufla.citogenetica.usuario.internal.web.dto.request;

import br.ufla.citogenetica.usuario.internal.domain.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitarTrocaEmailRequestDTO(
        @NotBlank(message = "Informe o novo e-mail")
        @Email(message = "Informe um endereço de e-mail válido")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
        String novoEmail,
        @NotBlank(message = "Informe a senha atual")
        String senhaAtual) {

    public SolicitarTrocaEmailRequestDTO {
        novoEmail = EmailNormalizer.normalize(novoEmail);
    }
}
