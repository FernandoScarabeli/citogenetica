package br.ufla.citogenetica.controller;

import br.ufla.citogenetica.model.notificacao.dto.NotificacaoResponseDTO;
import br.ufla.citogenetica.model.usuario.UsuarioPrincipal;
import br.ufla.citogenetica.shared.pagination.PaginationConstants;
import br.ufla.citogenetica.shared.pagination.PageableFactory;
import br.ufla.citogenetica.service.NotificacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notificacoes")
@RequiredArgsConstructor
@Tag(name = "Notificações", description = "Endpoints para gerenciamento de notificações (sininho)")
@SecurityRequirement(name = "sessao-cookie")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    @GetMapping
    @Operation(summary = "Listar notificações", description = "Retorna lista paginada das notificações do usuário autenticado. Use ?lida=false para buscar apenas não lidas.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de paginação inválidos")
    })
    public ResponseEntity<Page<NotificacaoResponseDTO>> listarNotificacoes(
            @AuthenticationPrincipal UsuarioPrincipal usuario,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "a página não pode ser negativa") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "o tamanho da página deve ser no mínimo 1")
            @Max(value = PaginationConstants.MAX_PAGE_SIZE, message = "o tamanho da página deve ser no máximo {value}") int size,
            @RequestParam(required = false) Boolean lida) {

        Pageable pageable = PageableFactory.criar(page, size);

        Page<NotificacaoResponseDTO> notificacoes;
        if (Boolean.FALSE.equals(lida)) {
            notificacoes = notificacaoService.buscarNaoLidas(usuario.id(), pageable);
        } else if (Boolean.TRUE.equals(lida)) {
            notificacoes = notificacaoService.buscarLidas(usuario.id(), pageable);
        } else {
            notificacoes = notificacaoService.buscarTodas(usuario.id(), pageable);
        }

        return ResponseEntity.ok(notificacoes);
    }

    @GetMapping("/nao-lidas/count")
    @Operation(summary = "Contar não lidas", description = "Retorna a quantidade de notificações não lidas (para o badge do sininho)")
    @ApiResponse(responseCode = "200", description = "Contagem retornada com sucesso")
    public ResponseEntity<Map<String, Long>> contarNaoLidas(@AuthenticationPrincipal UsuarioPrincipal usuario) {
        long count = notificacaoService.contarNaoLidas(usuario.id());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PatchMapping("/{id}/lida")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Marcar como lida", description = "Marca como lida uma notificação do usuário autenticado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notificação marcada como lida"),
            @ApiResponse(responseCode = "404", description = "Notificação não encontrada ou de outro usuário")
    })
    public ResponseEntity<NotificacaoResponseDTO> marcarComoLida(
            @AuthenticationPrincipal UsuarioPrincipal usuario,
            @PathVariable Long id) {
        NotificacaoResponseDTO notificacao = notificacaoService.marcarComoLida(usuario.id(), id);
        return ResponseEntity.ok(notificacao);
    }

    @PatchMapping("/marcar-todas-lidas")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Marcar todas como lidas", description = "Marca como lidas todas as notificações não lidas do usuário autenticado")
    @ApiResponse(responseCode = "200", description = "Notificações marcadas como lidas")
    public ResponseEntity<Map<String, Integer>> marcarTodasComoLidas(@AuthenticationPrincipal UsuarioPrincipal usuario) {
        int count = notificacaoService.marcarTodasComoLidas(usuario.id());
        return ResponseEntity.ok(Map.of("marcadas", count));
    }
}

