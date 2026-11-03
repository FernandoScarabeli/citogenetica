package br.ufla.citogenetica.laboratorio.internal.web;

import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.application.OrientacaoService;
import br.ufla.citogenetica.laboratorio.internal.application.VinculoOrientacaoResumo;
import br.ufla.citogenetica.laboratorio.internal.web.dto.response.VinculoOrientacaoResponseDTO;
import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import br.ufla.citogenetica.usuario.api.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/laboratorios/{laboratorioId}/orientacoes")
@RequiredArgsConstructor
@Validated
@Tag(name = "Orientações", description = "Consulta e encerramento de vínculos ativos de orientação")
@SecurityRequirement(name = "sessao-cookie")
public class OrientacaoVinculoController {

    private final OrientacaoService orientacaoService;

    @GetMapping
    @Operation(summary = "Listar vínculos de orientação visíveis",
            description = "Orientadores e pesquisadores veem seus vínculos ativos; administradores veem todos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vínculos visíveis retornados"),
            @ApiResponse(responseCode = "404", description = "Laboratório inexistente ou sem participação ativa")
    })
    public ResponseEntity<List<VinculoOrientacaoResponseDTO>> listar(
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        List<VinculoOrientacaoResponseDTO> vinculos = orientacaoService
                .listarVinculos(UUID.fromString(laboratorioId), principal.id()).stream()
                .map(OrientacaoVinculoController::resposta)
                .toList();
        return ResponseEntity.ok(vinculos);
    }

    @DeleteMapping("/{vinculoId}")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Encerrar vínculo de orientação",
            description = "O orientador do vínculo ou um administrador do laboratório pode encerrá-lo. O pesquisador não pode encerrá-lo.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vínculo encerrado"),
            @ApiResponse(responseCode = "403", description = "Pesquisador ou participante sem permissão tentou encerrar"),
            @ApiResponse(responseCode = "404", description = "Laboratório ou vínculo não encontrado"),
            @ApiResponse(responseCode = "409", description = "Vínculo já encerrado")
    })
    public ResponseEntity<Void> encerrar(
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @PathVariable @CanonicalUuidV4 String vinculoId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        orientacaoService.encerrarVinculo(
                UUID.fromString(laboratorioId), UUID.fromString(vinculoId), principal.id());
        return ResponseEntity.noContent().build();
    }

    private static VinculoOrientacaoResponseDTO resposta(VinculoOrientacaoResumo resumo) {
        return new VinculoOrientacaoResponseDTO(resumo.id(), resumo.laboratorioId(), resumo.orientadorUsuarioId(),
                resumo.nomeOrientador(), resumo.pesquisadorUsuarioId(), resumo.nomePesquisador(), resumo.criadoEm());
    }
}
