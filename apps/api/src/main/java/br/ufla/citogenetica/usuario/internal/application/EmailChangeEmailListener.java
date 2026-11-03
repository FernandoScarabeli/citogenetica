package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.shared.email.EmailSender;
import br.ufla.citogenetica.usuario.internal.domain.event.EmailAddressChangedEvent;
import br.ufla.citogenetica.usuario.internal.domain.event.EmailChangeConfirmationRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@Slf4j
public class EmailChangeEmailListener {

    private final EmailSender emailSender;

    public EmailChangeEmailListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async("recoveryEmailExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void enviarLinkDeConfirmacao(EmailChangeConfirmationRequestedEvent event) {
        try {
            emailSender.sendEmail(event.email(), "Confirme a alteração do seu e-mail — Citogenética UFLA",
                    EmailTemplates.templateConfirmacaoTrocaEmail(event.confirmationUrl(), event.expirationHours()));
        } catch (RuntimeException exception) {
            log.warn("Falha ao enviar o link de confirmação da troca de e-mail.");
        }
    }

    @Async("recoveryEmailExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void avisarEnderecoAnterior(EmailAddressChangedEvent event) {
        try {
            emailSender.sendEmail(event.previousEmail(), "Aviso de alteração do e-mail — Citogenética UFLA",
                    EmailTemplates.templateAvisoTrocaEmail());
        } catch (RuntimeException exception) {
            log.warn("Falha ao notificar o endereço de e-mail anterior.");
        }
    }
}
