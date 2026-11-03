package br.ufla.citogenetica.infra.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${api.email.from}")
    private String fromEmail;

    public void sendEmail(String toEmail, String emailSubject, String emailBody) {
        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("Email de destino não pode ser vazio");
        }

        if (emailSubject == null || emailSubject.isBlank()) {
            throw new IllegalArgumentException("Assunto do email não pode ser vazio");
        }

        if (emailBody == null || emailBody.isBlank()) {
            throw new IllegalArgumentException("Corpo do email não pode ser vazio");
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(emailSubject);
            helper.setText(emailBody, true);

            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new RuntimeException("Falha ao enviar email para: " + toEmail, e);
        }
    }
}
