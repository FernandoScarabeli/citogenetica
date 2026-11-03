package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.shared.email.EmailSender;
import br.ufla.citogenetica.usuario.internal.domain.event.RecoveryEmailRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@Slf4j
public class RecoveryEmailListener {

    private final EmailSender emailSender;

    public RecoveryEmailListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async("recoveryEmailExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void enviarCodigo(RecoveryEmailRequestedEvent event) {
        try {
            emailSender.sendEmail(
                    event.email(),
                    "Seu código de recuperação de senha",
                    EmailTemplates.templateRecuperacaoSenha(event.code(), event.expirationMinutes()));
        } catch (RuntimeException exception) {
            log.warn("Falha ao enviar e-mail de recuperação. Solicite um novo código.");
        }
    }
}
