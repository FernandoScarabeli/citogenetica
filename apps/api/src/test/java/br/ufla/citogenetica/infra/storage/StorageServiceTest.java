package br.ufla.citogenetica.infra.storage;

import br.ufla.citogenetica.shared.secret.SecretValue;
import br.ufla.citogenetica.shared.web.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageServiceTest {

    private static final String ACCESS_KEY = "LOCALTESTACCESSKEY";
    private final String secretKey = UUID.randomUUID().toString();
    private final S3Client s3Client = mock(S3Client.class);
    private final StorageProperties properties = new StorageProperties(
            "http://rustfs:9000",
            "http://storage.example.test:9000",
            "us-east-1",
            true,
            "citogenetica",
            new SecretValue(ACCESS_KEY),
            new SecretValue(secretKey),
            900);
    private final S3Presigner presigner = createPresigner();
    private final StorageService storageService = new StorageService(s3Client, presigner, properties);

    @AfterEach
    void closePresigner() {
        presigner.close();
    }

    @Test
    void leavesAnExistingBucketUnchanged() {
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());

        storageService.ensureBucketExists();

        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void createsAMissingBucket() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        storageService.ensureBucketExists();

        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void failsClearlyWhenBucketAccessIsDenied() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).build());

        assertThatThrownBy(storageService::ensureBucketExists)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Falha ao acessar o armazenamento");
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void signsPutAndGetUrlsForThePublicEndpointAndConfiguredValidity() {
        Instant beforeSigning = Instant.now();
        SignedStorageUrl upload = storageService.generateUploadUrl(".JPG");
        SignedStorageUrl read = storageService.generateReadUrl(upload.objectKey());
        Instant afterSigning = Instant.now();

        assertThat(upload.method()).isEqualTo(HttpMethod.PUT);
        assertThat(read.method()).isEqualTo(HttpMethod.GET);
        assertThat(upload.objectKey()).matches("fotos/[0-9a-f-]{36}\\.jpg");
        assertThat(read.objectKey()).isEqualTo(upload.objectKey());
        assertThat(upload.url().getHost()).isEqualTo("storage.example.test");
        assertThat(read.url().getHost()).isEqualTo("storage.example.test");
        assertThat(upload.url().getPath()).startsWith("/citogenetica/fotos/");
        assertThat(read.url().getPath()).startsWith("/citogenetica/fotos/");
        assertThat(upload.url().getRawQuery()).contains("X-Amz-Expires=900");
        assertThat(read.url().getRawQuery()).contains("X-Amz-Expires=900");
        assertThat(upload.expiresAt()).isBetween(beforeSigning.plusSeconds(900), afterSigning.plusSeconds(900));
        assertThat(read.expiresAt()).isBetween(beforeSigning.plusSeconds(900), afterSigning.plusSeconds(900));
        assertThat(upload.url().getUserInfo()).isNull();
        assertThat(read.url().getUserInfo()).isNull();
        // SigV4 inclui o identificador da chave em X-Amz-Credential; o segredo nunca deve ser serializado.
        assertThat(upload.url().toString()).contains(ACCESS_KEY).doesNotContain(secretKey);
        assertThat(read.url().toString()).contains(ACCESS_KEY).doesNotContain(secretKey);
    }

    @Test
    void rejectsArbitraryObjectKeysAndUnsafeExtensions() {
        assertThatThrownBy(() -> storageService.generateReadUrl("fotos/../../etc/passwd"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storageService.generateReadUrl(
                "fotos/123E4567-E89B-12D3-A456-426614174000.jpg"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storageService.generateReadUrl(
                "fotos/123e4567-e89b-12d3-a456-426614174000.JPG"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storageService.generateUploadUrl("../jpg"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A extensão do arquivo é inválida.");
        assertThatThrownBy(() -> storageService.generateUploadUrl(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A extensão do arquivo é obrigatória.");
    }

    private S3Presigner createPresigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.publicEndpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKeyValue(), properties.secretKeyValue())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
