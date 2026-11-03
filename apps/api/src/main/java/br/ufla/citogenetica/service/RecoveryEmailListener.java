package br.ufla.citogenetica.service;

import br.ufla.citogenetica.infra.email.EmailService;
import br.ufla.citogenetica.model.usuario.RecoveryEmailRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@Slf4j
public class RecoveryEmailListener {

    private final EmailService emailService;

    public RecoveryEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async("recoveryEmailExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void enviarCodigo(RecoveryEmailRequestedEvent event) {
        try {
            emailService.sendEmail(
                    event.email(),
                    "Seu código de recuperação de senha",
                    EmailTemplates.templateRecuperacaoSenha(event.code(), event.expirationMinutes()));
        } catch (RuntimeException exception) {
            log.warn("Falha ao enviar e-mail de recuperação. Solicite um novo código.");
        }
    }
}
