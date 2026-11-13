package br.ufla.citogenetica.infra.storage;

import br.ufla.citogenetica.shared.secret.SecretValue;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoragePropertiesTest {

    @Test
    void rejectsInvalidStorageConfiguration() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            StorageProperties properties = new StorageProperties(
                    "ftp://storage",
                    "bad region",
                    true,
                    "BadBucket",
                    new SecretValue("invalid/key"),
                    new SecretValue("short"));

            assertThat(factory.getValidator().validate(properties))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("endpointsValid", "region", "bucket", "accessKeyFormatValid", "secretKey");
        }
    }
}
