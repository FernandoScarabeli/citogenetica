package br.ufla.citogenetica.service;

import br.ufla.citogenetica.configuration.StorageProperties;
import br.ufla.citogenetica.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class StorageService {

    private static final Pattern EXTENSION_PATTERN = Pattern.compile("[a-z0-9]{1,10}");
    private static final Pattern MANAGED_OBJECT_KEY_PATTERN = Pattern.compile(
            "fotos/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[a-z0-9]{1,10}");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    public StorageService(S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @PostConstruct
    public void initializeBucket() {
        ensureBucketExists();
    }

    void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
            return;
        } catch (S3Exception exception) {
            if (!isBucketMissing(exception)) {
                throw storageUnavailable(exception);
            }
        } catch (SdkClientException exception) {
            throw storageUnavailable(exception);
        }

        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (S3Exception exception) {
            if ("BucketAlreadyOwnedByYou".equals(errorCode(exception))) {
                return;
            }
            throw storageUnavailable(exception);
        } catch (SdkClientException exception) {
            throw storageUnavailable(exception);
        }
    }

    public SignedStorageUrl generateUploadUrl(String fileExtension) {
        String extension = normalizeExtension(fileExtension);
        String objectKey = "fotos/" + UUID.randomUUID() + "." + extension;
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(urlValidity())
                .putObjectRequest(putObjectRequest)
                .build();

        URI url = URI.create(s3Presigner.presignPutObject(presignRequest).url().toString());
        return new SignedStorageUrl(objectKey, HttpMethod.PUT, url, expiryInstant());
    }

    /** Aceita apenas uma chave gerada por este serviço e rejeita chaves arbitrárias. */
    public SignedStorageUrl generateReadUrl(String objectKey) {
        validateManagedObjectKey(objectKey);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(urlValidity())
                .getObjectRequest(getObjectRequest)
                .build();

        URI url = URI.create(s3Presigner.presignGetObject(presignRequest).url().toString());
        return new SignedStorageUrl(objectKey, HttpMethod.GET, url, expiryInstant());
    }

    private String normalizeExtension(String fileExtension) {
        if (fileExtension == null || fileExtension.isBlank()) {
            throw new BusinessException("A extensão do arquivo é obrigatória.");
        }

        String extension = fileExtension.startsWith(".") ? fileExtension.substring(1) : fileExtension;
        extension = extension.toLowerCase(Locale.ROOT);
        if (!EXTENSION_PATTERN.matcher(extension).matches()) {
            throw new BusinessException("A extensão do arquivo é inválida.");
        }
        return extension;
    }

    private void validateManagedObjectKey(String objectKey) {
        if (objectKey == null || !MANAGED_OBJECT_KEY_PATTERN.matcher(objectKey).matches()) {
            throw new BusinessException("A chave de objeto não foi gerada pelo armazenamento.");
        }
    }

    private Duration urlValidity() {
        return Duration.ofSeconds(properties.presignedUrlValiditySeconds());
    }

    private Instant expiryInstant() {
        return Instant.now().plus(urlValidity());
    }

    private StorageException storageUnavailable(Throwable cause) {
        return new StorageException("Falha ao acessar o armazenamento ou inicializar o bucket configurado.", cause);
    }

    private boolean isBucketMissing(S3Exception exception) {
        return exception.statusCode() == 404 || "NoSuchBucket".equals(errorCode(exception));
    }

    private String errorCode(S3Exception exception) {
        return exception.awsErrorDetails() == null ? null : exception.awsErrorDetails().errorCode();
    }
}
