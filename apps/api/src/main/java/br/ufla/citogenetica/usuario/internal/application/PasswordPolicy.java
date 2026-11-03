package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.usuario.api.error.PasswordPolicyException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PasswordPolicy {

    private static final int MIN_CHARACTERS = 8;
    private static final int MAX_CHARACTERS = 64;
    private static final int BCRYPT_MAX_BYTES = 72;
    private final Set<String> commonPasswords;

    public PasswordPolicy() {
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("password-blocklist/common-passwords.txt").getInputStream(), StandardCharsets.UTF_8))) {
            commonPasswords = reader.lines()
                    .map(String::strip)
                    .filter(value -> !value.isEmpty())
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível carregar a lista local de senhas comuns.", exception);
        }
    }

    public void validate(String password) {
        if (password == null) {
            throw new PasswordPolicyException("É obrigatório informar a senha.");
        }

        int characterCount = password.codePointCount(0, password.length());
        if (characterCount < MIN_CHARACTERS || characterCount > MAX_CHARACTERS) {
            throw new PasswordPolicyException("A senha deve ter entre 8 e 64 caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new PasswordPolicyException("A senha excede o limite de 72 bytes em UTF-8 do BCrypt.");
        }
        if (commonPasswords.contains(password.toLowerCase(Locale.ROOT))) {
            throw new PasswordPolicyException("Escolha uma senha que não seja comum ou conhecida em vazamentos.");
        }
    }
}
