package br.ufla.citogenetica.shared.validation;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalUuidV4ValidatorTest {

    private final CanonicalUuidV4Validator validator = new CanonicalUuidV4Validator();

    @Test
    void aceitaUuidV4CanonicoEmMinusculas() {
        assertTrue(validator.isValid("550e8400-e29b-41d4-a716-446655440000", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "550e8400-e29b-11d4-a716-446655440000", // v1
            "01890f3e-7cc0-7000-8000-000000000000", // v7
            "550E8400-E29B-41D4-A716-446655440000", // maiúsculas
            "550e8400e29b41d4a716446655440000", // sem forma canônica
            "550e8400-e29b-41d4-7716-446655440000", // variante não RFC
            "550e8400-e29b-41d4-a716-44665544000" // tamanho inválido
    })
    void rejeitaUuidQueNaoSejaV4Canonico(String valor) {
        assertFalse(validator.isValid(valor, null));
    }

    @Test
    void mensagemDeValidacaoEGenericaENaoRepeteOValorRecebido() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var violacao = validator.validate(new UuidRequest("valor-sensivel-invalido")).iterator().next();

        assertEquals("UUID inválido.", violacao.getMessage());
        assertFalse(violacao.getMessage().contains("valor-sensivel-invalido"));
    }

    private record UuidRequest(@CanonicalUuidV4 String id) {
    }
}
