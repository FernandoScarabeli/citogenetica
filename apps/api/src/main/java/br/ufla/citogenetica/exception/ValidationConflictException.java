package br.ufla.citogenetica.exception;

public class ValidationConflictException extends RuntimeException {
    public ValidationConflictException(String message) {
        super(message);
    }
}
