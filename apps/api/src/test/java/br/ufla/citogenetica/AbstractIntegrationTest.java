package br.ufla.citogenetica;

import br.ufla.citogenetica.config.TestSecrets;
import br.ufla.citogenetica.config.TestcontainersConfiguration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Base dos testes de integração: PostgreSQL via Testcontainers e propriedades isoladas.
 * As propriedades são registradas com {@link DynamicPropertySource}, que tem precedência sobre
 * variáveis de ambiente; assim, um {@code .env} carregado no shell não vaza para os testes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    private static final String RECOVERY_HMAC_SECRET = TestSecrets.generate();
    private static final String EMAIL_PASSWORD = TestSecrets.generate();
    private static final String STORAGE_ACCESS_KEY = "T" + TestSecrets.generate();
    private static final String STORAGE_SECRET_KEY = TestSecrets.generate();

    /** O storage verifica o bucket na inicialização; o RustFS real é coberto por StorageServiceRustFsIntegrationTest. */
    @MockBean
    protected S3Client s3Client;

    @DynamicPropertySource
    static void registerTestProperties(DynamicPropertyRegistry properties) {
        properties.add("api.security.recovery.hmac-secret", () -> RECOVERY_HMAC_SECRET);
        properties.add("api.cors.allowed-origins", () -> "http://localhost:5173");
        properties.add("api.frontend.url", () -> "http://localhost:3000");
        properties.add("api.security.email-confirmation.frontend-url", () -> "https://front.citogenetica.test/confirmar-email");
        properties.add("api.security.email-change-confirmation.frontend-url", () -> "https://front.citogenetica.test/confirmar-troca-email");
        properties.add("api.security.laboratory-invitations.frontend-url", () -> "https://front.citogenetica.test/convites/aceitar");
        properties.add("api.security.orientation-invitations.frontend-url", () -> "https://front.citogenetica.test/orientacoes/convites/aceitar");
        properties.add("api.email.from", () -> "testes@citogenetica.test");

        properties.add("app.datasource.password", () -> TestcontainersConfiguration.DATABASE_PASSWORD);

        properties.add("app.email.host", () -> "localhost");
        properties.add("app.email.port", () -> 1025);
        properties.add("app.email.username", () -> "citogenetica-test");
        properties.add("app.email.password", () -> EMAIL_PASSWORD);
        properties.add("app.email.auth", () -> false);
        properties.add("app.email.starttls-enable", () -> false);

        properties.add("storage.access-key", () -> STORAGE_ACCESS_KEY);
        properties.add("storage.secret-key", () -> STORAGE_SECRET_KEY);
        properties.add("storage.bucket", () -> "citogenetica-test");
    }
}
