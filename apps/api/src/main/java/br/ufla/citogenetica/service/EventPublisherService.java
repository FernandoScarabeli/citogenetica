package br.ufla.citogenetica.service;

import br.ufla.citogenetica.config.sse.SseProperties;
import br.ufla.citogenetica.model.event.NotificacaoCriadaEvent;
import br.ufla.citogenetica.model.event.ServerEvent;
import br.ufla.citogenetica.model.event.SessoesRevogadasEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Conexões SSE agrupadas por usuário e amarradas à sessão que as abriu.
 * Eventos vão só para o destinatário; a conexão cai junto com a sessão.
 */
@Service
@Slf4j
public class EventPublisherService {

    private final Map<Long, List<Conexao>> conexoesPorUsuario = new ConcurrentHashMap<>();
    private final SseProperties properties;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public EventPublisherService(SseProperties properties,
                                 FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.properties = properties;
        this.sessionRepository = sessionRepository;
    }

    private record Conexao(String sessionId, SseEmitter emitter) {
    }

    /** Retorna vazio quando o usuário já atingiu o limite de conexões simultâneas. */
    public Optional<SseEmitter> subscribe(Long usuarioId, String sessionId) {
        SseEmitter emitter = new SseEmitter(properties.timeout().toMillis());
        Conexao conexao = new Conexao(sessionId, emitter);

        // compute é atômico por chave: duas assinaturas simultâneas não passam juntas do limite.
        boolean[] aceita = {false};
        conexoesPorUsuario.compute(usuarioId, (id, conexoes) -> {
            List<Conexao> lista = conexoes == null ? new CopyOnWriteArrayList<>() : conexoes;
            if (lista.size() < properties.maxConnectionsPerUser()) {
                lista.add(conexao);
                aceita[0] = true;
            }
            return lista.isEmpty() ? null : lista;
        });
        if (!aceita[0]) {
            return Optional.empty();
        }

        emitter.onCompletion(() -> remover(usuarioId, conexao));
        emitter.onTimeout(emitter::complete);
        emitter.onError(erro -> remover(usuarioId, conexao));

        enviar(usuarioId, conexao, SseEmitter.event().name("connected").data("Conexão SSE estabelecida"));
        return Optional.of(emitter);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoCriarNotificacao(NotificacaoCriadaEvent evento) {
        ServerEvent payload = new ServerEvent(evento.notificacao());
        conexoesDe(evento.destinatarioId()).forEach(conexao -> enviar(evento.destinatarioId(), conexao,
                SseEmitter.event().name("notification").data(payload)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoRevogarSessoes(SessoesRevogadasEvent evento) {
        encerrar(evento.usuarioId(), conexao -> !conexao.sessionId().equals(evento.sessionIdMantido()));
    }

    /** Encerra as conexões abertas pela sessão informada (logout). */
    public void encerrarSessao(Long usuarioId, String sessionId) {
        encerrar(usuarioId, conexao -> conexao.sessionId().equals(sessionId));
    }

    /**
     * A cada 30 segundos: encerra conexões cuja sessão expirou ou foi revogada (inclusive por outra
     * instância da API) e envia heartbeat às demais, detectando clientes que sumiram.
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        conexoesPorUsuario.keySet().forEach(usuarioId -> {
            Set<String> sessoesValidas = sessoesValidas(usuarioId);
            encerrar(usuarioId, conexao -> !sessoesValidas.contains(conexao.sessionId()));
            conexoesDe(usuarioId).forEach(conexao ->
                    enviar(usuarioId, conexao, SseEmitter.event().comment("heartbeat")));
        });
    }

    private Set<String> sessoesValidas(Long usuarioId) {
        return sessionRepository.findByPrincipalName(usuarioId.toString()).entrySet().stream()
                .filter(sessao -> !sessao.getValue().isExpired())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private void encerrar(Long usuarioId, Predicate<Conexao> criterio) {
        conexoesDe(usuarioId).stream().filter(criterio).forEach(conexao -> {
            remover(usuarioId, conexao);
            conexao.emitter().complete();
        });
    }

    private void enviar(Long usuarioId, Conexao conexao, SseEmitter.SseEventBuilder evento) {
        try {
            conexao.emitter().send(evento);
        } catch (IOException | IllegalStateException e) {
            log.debug("Conexão SSE do usuário {} indisponível; removendo", usuarioId);
            remover(usuarioId, conexao);
            conexao.emitter().completeWithError(e);
        }
    }

    private List<Conexao> conexoesDe(Long usuarioId) {
        return conexoesPorUsuario.getOrDefault(usuarioId, List.of());
    }

    private void remover(Long usuarioId, Conexao conexao) {
        conexoesPorUsuario.computeIfPresent(usuarioId, (id, conexoes) -> {
            conexoes.remove(conexao);
            return conexoes.isEmpty() ? null : conexoes;
        });
    }
}
