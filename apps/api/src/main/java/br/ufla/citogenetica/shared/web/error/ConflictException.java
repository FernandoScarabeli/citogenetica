package br.ufla.citogenetica.shared.web.error;

/** Conflito genérico de estado, concorrência ou constraint esperada. */
public class ConflictException extends RuntimeException {

    public ConflictException(String mensagem) {
        super(mensagem);
    }
}
