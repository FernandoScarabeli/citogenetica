package br.ufla.citogenetica.controller;

import br.ufla.citogenetica.service.EventPublisherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Eventos SSE", description = "Endpoint para conexão Server-Sent Events (notificações em tempo real)")
public class EventController {

    private final EventPublisherService eventPublisherService;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Inscrever-se para eventos SSE",
            description = "Abre uma conexão SSE para receber notificações em tempo real. " +
                    "O frontend deve usar EventSource para se conectar a este endpoint."
    )
    @ApiResponse(responseCode = "200", description = "Conexão SSE estabelecida")
    public SseEmitter subscribe() {
        return eventPublisherService.subscribe();
    }

    @GetMapping("/subscribers/count")
    @Operation(summary = "Contar inscritos", description = "Retorna a quantidade de conexões SSE ativas")
    @ApiResponse(responseCode = "200", description = "Contagem retornada")
    public Map<String, Integer> getSubscriberCount() {
        return Map.of("count", eventPublisherService.getSubscriberCount());
    }
}

