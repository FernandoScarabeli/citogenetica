package br.ufla.citogenetica.shared.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

/** Cria identificadores UUIDv4 para registros criados pelo servidor. */
@Component
public class UuidFactory {

    public UUID generate() {
        return UUID.randomUUID();
    }
}
