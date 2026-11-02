package br.ufla.citogenetica.controller;

import br.ufla.citogenetica.model.notificacao.dto.NotificacaoResponseDTO;
import br.ufla.citogenetica.service.NotificacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notificacoes")
@RequiredArgsConstructor
@Tag(name = "Notificações", description = "Endpoints para gerenciamento de notificações (sininho)")
@SecurityRequirement(name = "bearer-jwt")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    @GetMapping
    @Operation(summary = "Listar notificações", description = "Retorna lista paginada de notificações. Use ?lida=false para buscar apenas não lidas.")
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    public ResponseEntity<Page<NotificacaoResponseDTO>> listarNotificacoes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean lida) {

        Pageable pageable = PageRequest.of(page, size);

        Page<NotificacaoResponseDTO> notificacoes;
        if (Boolean.FALSE.equals(lida)) {
            notificacoes = notificacaoService.buscarNaoLidas(pageable);
        } else {
            notificacoes = notificacaoService.buscarTodas(pageable);
        }

        return ResponseEntity.ok(notificacoes);
    }

    @GetMapping("/nao-lidas/count")
    @Operation(summary = "Contar não lidas", description = "Retorna a quantidade de notificações não lidas (para o badge do sininho)")
    @ApiResponse(responseCode = "200", description = "Contagem retornada com sucesso")
    public ResponseEntity<Map<String, Long>> contarNaoLidas() {
        long count = notificacaoService.contarNaoLidas();
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PatchMapping("/{id}/lida")
    @Operation(summary = "Marcar como lida", description = "Marca uma notificação específica como lida")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notificação marcada como lida"),
            @ApiResponse(responseCode = "404", description = "Notificação não encontrada")
    })
    public ResponseEntity<NotificacaoResponseDTO> marcarComoLida(@PathVariable Long id) {
        NotificacaoResponseDTO notificacao = notificacaoService.marcarComoLida(id);
        return ResponseEntity.ok(notificacao);
    }

    @PatchMapping("/marcar-todas-lidas")
    @Operation(summary = "Marcar todas como lidas", description = "Marca todas as notificações não lidas como lidas")
    @ApiResponse(responseCode = "200", description = "Notificações marcadas como lidas")
    public ResponseEntity<Map<String, Integer>> marcarTodasComoLidas() {
        int count = notificacaoService.marcarTodasComoLidas();
        return ResponseEntity.ok(Map.of("marcadas", count));
    }
}

