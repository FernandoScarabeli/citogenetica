package br.ufla.citogenetica.infra.storage;

import br.ufla.citogenetica.shared.storage.ArmazenamentoIndisponivelException;

public class StorageException extends ArmazenamentoIndisponivelException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
