package br.ufla.citogenetica.configuration;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Segredos aleatórios gerados a cada execução dos testes, válidos para a validação de segredos da aplicação.
 */
public final class TestSecrets {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TestSecrets() {
    }

    public static String generate() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
