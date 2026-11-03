package br.ufla.citogenetica.shared.filter;

/** Filtro, ordenação ou valor de filtro inválido enviado pelo cliente (400). */
public class FiltroInvalidoException extends RuntimeException {

    public FiltroInvalidoException(String message) {
        super(message);
    }
}
