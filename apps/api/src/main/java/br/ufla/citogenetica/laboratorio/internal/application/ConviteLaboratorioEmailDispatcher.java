package br.ufla.citogenetica.laboratorio.internal.application;

import java.util.List;

import br.ufla.citogenetica.shared.email.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ConviteLaboratorioEmailDispatcher {

    private final EmailSender emailSender;

    public ConviteLaboratorioEmailDispatcher(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async("conviteEmailExecutor")
    public void enviar(java.util.UUID conviteId, String emailDestino, String laboratorioNome,
                       List<String> papeis, String link, int validadeDias) {
        enviarInterno(conviteId, emailDestino,
                "Convite para participar de um laboratório — Citogenética UFLA",
                ConviteEmailTemplate.criarParaLaboratorio(laboratorioNome, papeis, link, validadeDias));
    }

    @Async("conviteEmailExecutor")
    public void enviarOrientacao(java.util.UUID conviteId, String emailDestino, String laboratorioNome,
                                 String link, int validadeDias) {
        enviarInterno(conviteId, emailDestino,
                "Convite de orientação — Citogenética UFLA",
                ConviteEmailTemplate.criarParaOrientacao(laboratorioNome, link, validadeDias));
    }

    private void enviarInterno(java.util.UUID conviteId, String emailDestino, String assunto, String corpo) {
        try {
            emailSender.sendEmail(emailDestino, assunto, corpo);
        } catch (RuntimeException exception) {
            log.warn("Falha ao enviar e-mail do convite {}. O administrador pode reenviar.", conviteId);
        }
    }
}
