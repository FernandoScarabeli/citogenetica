package br.ufla.citogenetica.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class RecoveryHmacService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String RECOVERY_CODE_DOMAIN = "citogenetica:recuperacao-senha:v1";
    private static final String EMAIL_RATE_DOMAIN = "citogenetica:rate-limit:email:v1";
    private static final String IP_RATE_DOMAIN = "citogenetica:rate-limit:ip:v1";

    private final byte[] secret;

    public RecoveryHmacService(RecoveryProperties properties) {
        this.secret = properties.hmacSecret().reveal().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] recoveryCode(UUID challengeId, String code) {
        return digest(RECOVERY_CODE_DOMAIN + ":" + challengeId + ":" + code);
    }

    public byte[] emailRateKey(String normalizedEmail) {
        return digest(EMAIL_RATE_DOMAIN + ":" + normalizedEmail);
    }

    public byte[] ipRateKey(String ipAddress) {
        return digest(IP_RATE_DOMAIN + ":" + ipAddress);
    }

    private byte[] digest(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível processar a verificação de segurança.");
        }
    }
}
