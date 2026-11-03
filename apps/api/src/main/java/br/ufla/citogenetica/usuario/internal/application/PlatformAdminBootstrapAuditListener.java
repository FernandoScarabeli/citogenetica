package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.usuario.internal.domain.event.PlatformAdminBootstrapCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@Slf4j
public class PlatformAdminBootstrapAuditListener {

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void registrarPromocao(PlatformAdminBootstrapCompletedEvent event) {
        log.info("Bootstrap do administrador global concluído para usuário id={}", event.usuarioId());
    }
}
