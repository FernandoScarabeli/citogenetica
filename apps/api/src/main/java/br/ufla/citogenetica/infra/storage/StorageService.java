package br.ufla.citogenetica.infra.storage;

import br.ufla.citogenetica.shared.storage.FormatoImagem;
import br.ufla.citogenetica.shared.storage.FotoLaminaStorage;
import br.ufla.citogenetica.shared.storage.FotoPerfilStorage;
import br.ufla.citogenetica.shared.web.error.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class StorageService implements FotoPerfilStorage, FotoLaminaStorage {

    private static final Pattern PROFILE_PHOTO_KEY_PATTERN = Pattern.compile(
            "perfis/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");
    private static final Pattern SLIDE_PHOTO_KEY_PATTERN = Pattern.compile(
            "laminas/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");

    private final S3Client s3Client;
    private final StorageProperties properties;

    public StorageService(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
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

    @Override
    public String salvarFotoPerfil(byte[] content, String extension, String contentType) {
        return putImage("perfis/" + UUID.randomUUID() + "." + extension, PROFILE_PHOTO_KEY_PATTERN, content, contentType);
    }

    @Override
    public byte[] lerFotoPerfil(String objectKey) {
        return getImage(objectKey, PROFILE_PHOTO_KEY_PATTERN);
    }

    @Override
    public void apagarFotoPerfil(String objectKey) {
        deleteImage(objectKey, PROFILE_PHOTO_KEY_PATTERN);
    }

    @Override
    public String salvarFotoLamina(byte[] content, FormatoImagem formato) {
        return putImage("laminas/" + UUID.randomUUID() + "." + formato.extensao(), SLIDE_PHOTO_KEY_PATTERN,
                content, formato.contentType());
    }

    @Override
    public byte[] lerFotoLamina(String objectKey) {
        return getImage(objectKey, SLIDE_PHOTO_KEY_PATTERN);
    }

    @Override
    public void apagarFotoLamina(String objectKey) {
        deleteImage(objectKey, SLIDE_PHOTO_KEY_PATTERN);
    }

    private String putImage(String objectKey, Pattern allowedKeys, byte[] content, String contentType) {
        validateImageKey(objectKey, allowedKeys);
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(objectKey)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception | SdkClientException exception) {
            throw storageUnavailable(exception);
        }
        return objectKey;
    }

    private byte[] getImage(String objectKey, Pattern allowedKeys) {
        validateImageKey(objectKey, allowedKeys);
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build()).asByteArray();
        } catch (S3Exception | SdkClientException exception) {
            throw storageUnavailable(exception);
        }
    }

    private void deleteImage(String objectKey, Pattern allowedKeys) {
        validateImageKey(objectKey, allowedKeys);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception | SdkClientException exception) {
            throw storageUnavailable(exception);
        }
    }

    private void validateImageKey(String objectKey, Pattern allowedKeys) {
        if (objectKey == null || !allowedKeys.matcher(objectKey).matches()) {
            throw new BusinessException("A chave da imagem é inválida.");
        }
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
