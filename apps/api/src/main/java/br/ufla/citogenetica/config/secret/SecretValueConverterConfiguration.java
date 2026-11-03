package br.ufla.citogenetica.config.secret;

import br.ufla.citogenetica.shared.secret.SecretValue;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

@Configuration(proxyBeanMethods = false)
public class SecretValueConverterConfiguration {

    @Bean
    @ConfigurationPropertiesBinding
    static Converter<String, SecretValue> secretValueConverter() {
        return new StringToSecretValueConverter();
    }

    private static final class StringToSecretValueConverter implements Converter<String, SecretValue> {

        @Override
        public SecretValue convert(String source) {
            return new SecretValue(source);
        }
    }
}
