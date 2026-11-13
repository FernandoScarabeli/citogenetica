package br.ufla.citogenetica.usuario.api.error;

public class PasswordPolicyException extends RuntimeException {

    public PasswordPolicyException(String message) {
        super(message);
    }
}
