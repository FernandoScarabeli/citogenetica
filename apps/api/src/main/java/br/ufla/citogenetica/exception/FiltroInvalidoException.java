package br.ufla.citogenetica.exception;

/** Filtro, ordenação ou valor de filtro inválido enviado pelo cliente (400). */
public class FiltroInvalidoException extends RuntimeException {

    public FiltroInvalidoException(String message) {
        super(message);
    }
}
