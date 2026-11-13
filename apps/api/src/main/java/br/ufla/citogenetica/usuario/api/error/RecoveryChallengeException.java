package br.ufla.citogenetica.usuario.api.error;

public class RecoveryChallengeException extends RuntimeException {

    public RecoveryChallengeException() {
        super("Código inválido ou expirado.");
    }
}
