package br.ufla.citogenetica.configuration.email;

import br.ufla.citogenetica.configuration.secret.SecretValue;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

class EmailConfigTest {

    @Test
    void appliesSmtpAuthenticationAndStarttlsFromTypedProperties() {
        EmailProperties properties = new EmailProperties(
                "smtp.example.test",
                587,
                "laboratorio@example.test",
                new SecretValue("unit-test-smtp-password"),
                true,
                true);

        JavaMailSenderImpl sender = (JavaMailSenderImpl) new EmailConfig().mailSender(properties);

        assertThat(sender.getHost()).isEqualTo("smtp.example.test");
        assertThat(sender.getPort()).isEqualTo(587);
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("true");
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
    }
}
