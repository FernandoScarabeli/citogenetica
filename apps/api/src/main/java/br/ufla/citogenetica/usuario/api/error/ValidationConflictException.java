package br.ufla.citogenetica.usuario.api.error;

public class ValidationConflictException extends RuntimeException {
    public ValidationConflictException(String message) {
        super(message);
    }
}
