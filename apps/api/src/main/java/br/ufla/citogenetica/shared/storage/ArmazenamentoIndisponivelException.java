package br.ufla.citogenetica.shared.storage;

/** O armazenamento de arquivos não respondeu ou recusou a operação. */
public class ArmazenamentoIndisponivelException extends RuntimeException {

    public ArmazenamentoIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
