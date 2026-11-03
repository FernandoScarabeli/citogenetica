package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.usuario.api.event.SessoesRevogadasEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class SessionRevocationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SessionRevocationService(FindByIndexNameSessionRepository<? extends Session> sessionRepository,
                                    ApplicationEventPublisher eventPublisher) {
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revogarSessoes(Long usuarioId, String sessionIdMantido) {
        Map<String, ? extends Session> sessoes = sessionRepository.findByPrincipalName(usuarioId.toString());
        sessoes.keySet().stream()
                .filter(sessionId -> sessionIdMantido == null || !sessionIdMantido.equals(sessionId))
                .forEach(sessionRepository::deleteById);
        // Conexões abertas (SSE) dessas sessões são encerradas depois do commit.
        eventPublisher.publishEvent(new SessoesRevogadasEvent(usuarioId, sessionIdMantido));
    }
}
