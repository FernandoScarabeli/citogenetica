package br.ufla.citogenetica.infra.storage;

import br.ufla.citogenetica.shared.secret.SecretValue;
import br.ufla.citogenetica.shared.web.error.BusinessException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.UUID;

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
            "us-east-1",
            true,
            "citogenetica",
            new SecretValue(ACCESS_KEY),
            new SecretValue(secretKey));
    private final StorageService storageService = new StorageService(s3Client, properties);

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
    void rejectsImageKeysOutsideTheManagedPrefixes() {
        assertThatThrownBy(() -> storageService.lerFotoLamina("laminas/../../etc/passwd"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storageService.lerFotoLamina(
                "perfis/123e4567-e89b-12d3-a456-426614174000.jpg"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storageService.apagarFotoPerfil(
                "perfis/123E4567-E89B-12D3-A456-426614174000.jpg"))
                .isInstanceOf(BusinessException.class);
    }
}
