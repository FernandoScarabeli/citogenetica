package br.ufla.citogenetica.laboratorio.internal.web;

import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.application.ConviteOrientacaoResumo;
import br.ufla.citogenetica.laboratorio.internal.application.OrientacaoService;
import br.ufla.citogenetica.laboratorio.internal.web.dto.request.CriarConviteOrientacaoRequestDTO;
import br.ufla.citogenetica.laboratorio.internal.web.dto.response.ConviteOrientacaoResponseDTO;
import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import br.ufla.citogenetica.usuario.api.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/laboratorios/{laboratorioId}/orientacoes/convites")
@RequiredArgsConstructor
@Validated
@Tag(name = "Convites de orientação", description = "Convites enviados por orientadores ativos do laboratório")
@SecurityRequirement(name = "sessao-cookie")
public class ConviteOrientacaoController {

    private final OrientacaoService orientacaoService;

    @PostMapping
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Convidar pesquisador para orientação",
            description = "O orientador ativo envia o convite por e-mail. Ao aceitar, uma participação MEMBRO é criada se necessário.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Convite criado e e-mail enviado após o commit"),
            @ApiResponse(responseCode = "400", description = "E-mail inválido"),
            @ApiResponse(responseCode = "403", description = "Participante não tem papel ORIENTADOR"),
            @ApiResponse(responseCode = "404", description = "Laboratório inexistente ou sem participação ativa do solicitante"),
            @ApiResponse(responseCode = "409", description = "Laboratório desativado, auto-orientação, vínculo ou convite pendente duplicado"),
            @ApiResponse(responseCode = "429", description = "Limite de envios atingido; a resposta inclui Retry-After")
    })
    public ResponseEntity<ConviteOrientacaoResponseDTO> convidar(
            @Parameter(description = "UUIDv4 canônico do laboratório")
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @RequestBody @Valid CriarConviteOrientacaoRequestDTO request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ConviteOrientacaoResumo resumo = orientacaoService.convidar(
                UUID.fromString(laboratorioId), principal.id(), request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta(resumo));
    }

    @GetMapping
    @Operation(summary = "Listar convites pendentes do orientador",
            description = "Cada orientador vê somente seus próprios convites pendentes e ainda válidos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Convites pendentes retornados"),
            @ApiResponse(responseCode = "403", description = "Participante não tem papel ORIENTADOR"),
            @ApiResponse(responseCode = "404", description = "Laboratório inexistente ou sem participação ativa do solicitante")
    })
    public ResponseEntity<List<ConviteOrientacaoResponseDTO>> listar(
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        List<ConviteOrientacaoResponseDTO> convites = orientacaoService
                .listarConvites(UUID.fromString(laboratorioId), principal.id()).stream()
                .map(ConviteOrientacaoController::resposta)
                .toList();
        return ResponseEntity.ok(convites);
    }

    @PostMapping("/{conviteId}/reenviar")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Reenviar convite de orientação",
            description = "Invalida o token anterior imediatamente e inicia novo prazo de validade.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Convite reenviado"),
            @ApiResponse(responseCode = "403", description = "Participante não tem papel ORIENTADOR"),
            @ApiResponse(responseCode = "404", description = "Convite não pertence ao orientador autenticado"),
            @ApiResponse(responseCode = "409", description = "Laboratório desativado ou convite não pode ser reenviado"),
            @ApiResponse(responseCode = "429", description = "Limite de envios atingido; a resposta inclui Retry-After")
    })
    public ResponseEntity<ConviteOrientacaoResponseDTO> reenviar(
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @PathVariable @CanonicalUuidV4 String conviteId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ConviteOrientacaoResumo resumo = orientacaoService.reenviarConvite(
                UUID.fromString(laboratorioId), UUID.fromString(conviteId), principal.id());
        return ResponseEntity.ok(resposta(resumo));
    }

    @DeleteMapping("/{conviteId}")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Revogar próprio convite de orientação",
            description = "Somente o orientador que enviou o convite pode revogá-lo; a revogação também é permitida em laboratório desativado.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Convite revogado"),
            @ApiResponse(responseCode = "403", description = "Participante não tem papel ORIENTADOR"),
            @ApiResponse(responseCode = "404", description = "Convite não pertence ao orientador autenticado"),
            @ApiResponse(responseCode = "409", description = "Convite não está mais pendente")
    })
    public ResponseEntity<Void> revogar(
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @PathVariable @CanonicalUuidV4 String conviteId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        orientacaoService.revogarConvite(UUID.fromString(laboratorioId), UUID.fromString(conviteId), principal.id());
        return ResponseEntity.noContent().build();
    }

    private static ConviteOrientacaoResponseDTO resposta(ConviteOrientacaoResumo resumo) {
        return new ConviteOrientacaoResponseDTO(resumo.id(), resumo.laboratorioId(), resumo.emailDestino(),
                resumo.estado(), resumo.expiraEm(), resumo.criadoEm());
    }
}
