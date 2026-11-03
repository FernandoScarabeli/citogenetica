package br.ufla.citogenetica.usuario.api.error;

public class EmailConfirmationException extends RuntimeException {

    public EmailConfirmationException() {
        super("Link de confirmação inválido ou expirado.");
    }
}
