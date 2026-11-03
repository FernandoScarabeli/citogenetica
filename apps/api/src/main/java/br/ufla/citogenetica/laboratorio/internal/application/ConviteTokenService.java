package br.ufla.citogenetica.laboratorio.internal.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/** Gera token bearer aleatório e persiste apenas seu hash SHA-256. */
@Component
public class ConviteTokenService {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom;

    public ConviteTokenService(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String gerar() {
        byte[] token = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    public byte[] hash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            if (decoded.length != TOKEN_BYTES) {
                return null;
            }
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | NoSuchAlgorithmException exception) {
            return null;
        }
    }

    public boolean iguais(byte[] primeiro, byte[] segundo) {
        return primeiro != null && segundo != null && MessageDigest.isEqual(primeiro, segundo);
    }
}
