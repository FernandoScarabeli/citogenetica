package br.ufla.citogenetica.infra.email;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration(proxyBeanMethods = false)
public class EmailConfig {

    @Bean
    public JavaMailSender mailSender(EmailProperties properties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.host());
        mailSender.setPort(properties.port());
        mailSender.setUsername(properties.username());
        mailSender.setPassword(properties.password().reveal());

        Properties mailProperties = mailSender.getJavaMailProperties();
        mailProperties.put("mail.transport.protocol", "smtp");
        mailProperties.put("mail.smtp.auth", Boolean.toString(properties.auth()));
        mailProperties.put("mail.smtp.starttls.enable", Boolean.toString(properties.starttlsEnable()));
        mailProperties.put("mail.smtp.connectiontimeout", "5000");
        mailProperties.put("mail.smtp.timeout", "5000");
        mailProperties.put("mail.smtp.writetimeout", "5000");

        return mailSender;
    }
}
