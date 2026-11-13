package br.ufla.citogenetica.infra.storage;

import br.ufla.citogenetica.shared.secret.SecretValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;
import org.springframework.http.HttpMethod;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class StorageServiceRustFsIntegrationTest {

    private static final String ACCESS_KEY = "TEST" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    private static final String SECRET_KEY = UUID.randomUUID() + UUID.randomUUID().toString();
    private static final String BUCKET = "citogenetica-test-" + UUID.randomUUID().toString().replace("-", "");
    private static final String FRONTEND_ORIGIN = "http://localhost:3000";

    @Container
    private static final GenericContainer<?> RUSTFS = new GenericContainer<>(DockerImageName.parse("rustfs/rustfs:1.0.3"))
            .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
            .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
            .withEnv("RUSTFS_ADDRESS", ":9000")
            .withEnv("RUSTFS_CONSOLE_ADDRESS", ":9001")
            .withEnv("RUSTFS_CONSOLE_ENABLE", "true")
            .withEnv("RUSTFS_CORS_ALLOWED_ORIGINS", FRONTEND_ORIGIN)
            .withExposedPorts(9000, 9001)
            .withCommand("/data")
            .waitingFor(Wait.forHttp("/health/ready")
                    .forPort(9000)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private static S3Client s3Client;
    private static S3Presigner presigner;
    private static StorageService storageService;
    private static String endpoint;

    @BeforeAll
    static void createClientsAndBucket() {
        endpoint = "http://" + RUSTFS.getHost() + ":" + RUSTFS.getMappedPort(9000);
        StorageProperties properties = new StorageProperties(
                endpoint,
                endpoint,
                "us-east-1",
                true,
                BUCKET,
                new SecretValue(ACCESS_KEY),
                new SecretValue(SECRET_KEY),
                300);
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY));
        S3Configuration s3Configuration = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                .build();
        storageService = new StorageService(s3Client, presigner, properties);
        storageService.initializeBucket();
    }

    @AfterAll
    static void closeClients() {
        if (presigner != null) {
            presigner.close();
        }
        if (s3Client != null) {
            s3Client.close();
        }
    }

    @Test
    void gravaLeEApagaFotoDePerfil() {
        byte[] foto = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 7, 7};

        String chave = storageService.salvarFotoPerfil(foto, "png", "image/png");

        assertThat(chave).matches("perfis/[0-9a-f-]{36}\\.png");
        assertThat(storageService.lerFotoPerfil(chave)).isEqualTo(foto);
        storageService.apagarFotoPerfil(chave);
        assertThatThrownBy(() -> storageService.lerFotoPerfil(chave)).isInstanceOf(StorageException.class);
    }

    @Test
    void uploadsAndReadsPrivatelyUsingPresignedUrls() throws Exception {
        byte[] payload = "rustfs integration".getBytes(StandardCharsets.UTF_8);
        SignedStorageUrl upload = storageService.generateUploadUrl("txt");
        HttpClient httpClient = HttpClient.newHttpClient();

        HttpResponse<byte[]> putResponse = httpClient.send(
                HttpRequest.newBuilder(upload.url())
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(payload))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(upload.method()).isEqualTo(HttpMethod.PUT);
        assertThat(putResponse.statusCode()).isEqualTo(200);

        SignedStorageUrl read = storageService.generateReadUrl(upload.objectKey());
        HttpResponse<byte[]> getResponse = httpClient.send(
                HttpRequest.newBuilder(read.url()).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(read.method()).isEqualTo(HttpMethod.GET);
        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(getResponse.body()).isEqualTo(payload);

        HttpResponse<String> preflightResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(endpoint + "/" + BUCKET + "/" + upload.objectKey()))
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "content-type")
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(preflightResponse.statusCode()).isBetween(200, 299);
        assertThat(preflightResponse.headers().firstValue("Access-Control-Allow-Origin"))
                .contains(FRONTEND_ORIGIN);

        URI anonymousObjectUri = URI.create(endpoint + "/" + BUCKET + "/" + upload.objectKey());
        HttpResponse<byte[]> anonymousResponse = httpClient.send(
                HttpRequest.newBuilder(anonymousObjectUri).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(anonymousResponse.statusCode()).isBetween(400, 499);
    }
}
