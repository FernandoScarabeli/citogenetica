package br.ufla.citogenetica.controller;

import br.ufla.citogenetica.model.usuario.UsuarioPrincipal;
import br.ufla.citogenetica.service.EventPublisherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Eventos SSE", description = "Endpoint para conexão Server-Sent Events (notificações em tempo real)")
@SecurityRequirement(name = "sessao-cookie")
public class EventController {

    private static final String RETRY_AFTER_SEGUNDOS = "30";

    private final EventPublisherService eventPublisherService;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Inscrever-se para eventos SSE",
            description = "Abre uma conexão SSE autenticada pelo cookie de sessão para receber as notificações " +
                    "do usuário autenticado. O frontend deve usar EventSource com withCredentials: true. " +
                    "A conexão é encerrada quando a sessão termina."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conexão SSE estabelecida"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou inválida"),
            @ApiResponse(responseCode = "429", description = "Limite de conexões simultâneas do usuário atingido")
    })
    public ResponseEntity<SseEmitter> subscribe(@AuthenticationPrincipal UsuarioPrincipal usuario, HttpSession sessao) {
        // Sem corpo no 429: o cliente pediu text/event-stream e o EventSource não lê o corpo de erros.
        return eventPublisherService.subscribe(usuario.id(), sessao.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SEGUNDOS)
                        .build());
    }
}
