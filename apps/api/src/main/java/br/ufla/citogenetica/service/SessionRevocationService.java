package br.ufla.citogenetica.service;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class SessionRevocationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public SessionRevocationService(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revogarSessoes(Long usuarioId, String sessionIdMantido) {
        Map<String, ? extends Session> sessoes = sessionRepository.findByPrincipalName(usuarioId.toString());
        sessoes.keySet().stream()
                .filter(sessionId -> sessionIdMantido == null || !sessionIdMantido.equals(sessionId))
                .forEach(sessionRepository::deleteById);
    }
}
