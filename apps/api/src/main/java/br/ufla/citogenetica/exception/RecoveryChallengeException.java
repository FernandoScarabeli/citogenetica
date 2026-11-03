package br.ufla.citogenetica.exception;

public class RecoveryChallengeException extends RuntimeException {

    public RecoveryChallengeException() {
        super("Código inválido ou expirado.");
    }
}
