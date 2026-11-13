package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.shared.validation.FrontendConfirmationUrlValidator;

final class EmailConfirmationUrlValidator {

    private EmailConfirmationUrlValidator() {
    }

    static boolean isValid(String frontendUrl) {
        return FrontendConfirmationUrlValidator.isValid(frontendUrl);
    }
}
