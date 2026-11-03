package br.ufla.citogenetica.laboratorio.internal.application;

import br.ufla.citogenetica.shared.validation.FrontendConfirmationUrlValidator;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "api.security.orientation-invitations")
public record ConviteOrientacaoProperties(@NotBlank String frontendUrl) {

    @AssertTrue(message = "A URL do convite de orientação deve ser HTTPS ou HTTP local e não pode conter fragmento")
    public boolean isFrontendUrlValid() {
        return FrontendConfirmationUrlValidator.isValid(frontendUrl);
    }
}
