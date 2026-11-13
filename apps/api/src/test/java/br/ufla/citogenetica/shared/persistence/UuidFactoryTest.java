package br.ufla.citogenetica.shared.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidFactoryTest {

    @Test
    void geraUuidV4ComVarianteRfc() {
        var uuid = new UuidFactory().generate();

        assertEquals(4, uuid.version());
        assertEquals(2, uuid.variant());
        assertTrue(uuid.toString().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
    }
}
