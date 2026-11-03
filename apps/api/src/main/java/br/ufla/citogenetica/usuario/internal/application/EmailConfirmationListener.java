package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.shared.email.EmailSender;
import br.ufla.citogenetica.usuario.internal.domain.event.EmailConfirmationRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@Slf4j
public class EmailConfirmationListener {

    private final EmailSender emailSender;

    public EmailConfirmationListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async("recoveryEmailExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void enviarLink(EmailConfirmationRequestedEvent event) {
        try {
            emailSender.sendEmail(event.email(), "Confirme seu e-mail — Citogenética UFLA",
                    EmailTemplates.templateConfirmacaoEmail(event.confirmationUrl(), event.expirationHours()));
        } catch (RuntimeException exception) {
            log.warn("Falha ao enviar e-mail de confirmação. Solicite um novo link.");
        }
    }
}
