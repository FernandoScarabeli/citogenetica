package br.ufla.citogenetica.service;

import br.ufla.citogenetica.model.event.ServerEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class EventPublisherService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutos

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> {
            log.debug("SSE connection completed");
            emitters.remove(emitter);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE connection timed out");
            emitter.complete();
            emitters.remove(emitter);
        });

        emitter.onError(e -> {
            log.debug("SSE connection error: {}", e.getMessage());
            emitters.remove(emitter);
        });

        emitters.add(emitter);
        log.info("New SSE subscriber connected. Total subscribers: {}", emitters.size());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Conexão SSE estabelecida"));
        } catch (IOException e) {
            log.error("Error sending initial SSE event", e);
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void publish(ServerEvent event) {
        log.info("Publishing event {} to {} subscribers", event.type(), emitters.size());

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(event));
            } catch (IOException e) {
                log.debug("Failed to send event to emitter, marking for removal");
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);

        if (!deadEmitters.isEmpty()) {
            log.info("Removed {} dead SSE connections", deadEmitters.size());
        }
    }

    public int getSubscriberCount() {
        return emitters.size();
    }

    /**
     * Envia heartbeat a cada 30 segundos para manter conexões vivas
     * e detectar conexões mortas rapidamente.
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        if (emitters.isEmpty()) {
            return;
        }

        log.debug("Sending heartbeat to {} subscribers", emitters.size());
        List<SseEmitter> deadEmitters = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            log.info("Heartbeat removed {} dead SSE connections", deadEmitters.size());
        }
    }
}

