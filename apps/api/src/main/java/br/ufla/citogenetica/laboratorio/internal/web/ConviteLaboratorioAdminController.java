package br.ufla.citogenetica.laboratorio.internal.web;

import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.application.ConviteLaboratorioResumo;
import br.ufla.citogenetica.laboratorio.internal.application.ConviteLaboratorioService;
import br.ufla.citogenetica.laboratorio.internal.web.dto.request.CriarConviteLaboratorioRequestDTO;
import br.ufla.citogenetica.laboratorio.internal.web.dto.response.ConviteLaboratorioResponseDTO;
import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import br.ufla.citogenetica.usuario.api.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/laboratorios/{laboratorioId}/convites")
@RequiredArgsConstructor
@Validated
@Tag(name = "Convites de laboratório", description = "Criação e administração de convites de participação")
@SecurityRequirement(name = "sessao-cookie")
public class ConviteLaboratorioAdminController {

    private final ConviteLaboratorioService conviteService;

    @PostMapping
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Convidar para o laboratório", description = "Somente administradores locais. O convite pode conceder papéis adicionais.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Convite criado e e-mail agendado após o commit"),
            @ApiResponse(responseCode = "400", description = "E-mail ou papéis inválidos"),
            @ApiResponse(responseCode = "403", description = "Participante sem papel de administrador"),
            @ApiResponse(responseCode = "404", description = "Laboratório não encontrado"),
            @ApiResponse(responseCode = "409", description = "Endereço já participa ou tem convite pendente"),
            @ApiResponse(responseCode = "429", description = "Limite de envios atingido")
    })
    public ResponseEntity<ConviteLaboratorioResponseDTO> criar(
            @Parameter(description = "UUIDv4 canônico do laboratório")
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @RequestBody @Valid CriarConviteLaboratorioRequestDTO request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ConviteLaboratorioResumo resumo = conviteService.criar(UUID.fromString(laboratorioId), principal.id(),
                request.email(), request.papeis());
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta(resumo));
    }

    @GetMapping
    @Operation(summary = "Listar convites pendentes", description = "Somente administradores locais; endereços convidados são exibidos para gestão.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Convites pendentes retornados"),
            @ApiResponse(responseCode = "403", description = "Participante sem papel de administrador"),
            @ApiResponse(responseCode = "404", description = "Laboratório não encontrado")
    })
    public ResponseEntity<List<ConviteLaboratorioResponseDTO>> listar(
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ResponseEntity.ok(conviteService.listar(UUID.fromString(laboratorioId), principal.id()).stream()
                .map(ConviteLaboratorioAdminController::resposta)
                .toList());
    }

    @PostMapping("/{conviteId}/reenviar")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Reenviar convite", description = "Substitui o token anterior e estende a validade.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Convite reenviado"),
            @ApiResponse(responseCode = "403", description = "Participante sem papel de administrador"),
            @ApiResponse(responseCode = "404", description = "Laboratório ou convite não encontrado"),
            @ApiResponse(responseCode = "409", description = "Convite não pode ser reenviado"),
            @ApiResponse(responseCode = "429", description = "Limite de envios atingido")
    })
    public ResponseEntity<ConviteLaboratorioResponseDTO> reenviar(
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @PathVariable @CanonicalUuidV4 String conviteId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ConviteLaboratorioResumo resumo = conviteService.reenviar(
                UUID.fromString(laboratorioId), UUID.fromString(conviteId), principal.id());
        return ResponseEntity.ok(resposta(resumo));
    }

    @DeleteMapping("/{conviteId}")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Revogar convite", description = "Somente administradores locais; invalida imediatamente o link pendente.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Convite revogado"),
            @ApiResponse(responseCode = "403", description = "Participante sem papel de administrador"),
            @ApiResponse(responseCode = "404", description = "Laboratório ou convite não encontrado"),
            @ApiResponse(responseCode = "409", description = "Convite não está mais pendente")
    })
    public ResponseEntity<Void> revogar(
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @PathVariable @CanonicalUuidV4 String conviteId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        conviteService.revogar(UUID.fromString(laboratorioId), UUID.fromString(conviteId), principal.id());
        return ResponseEntity.noContent().build();
    }

    private static ConviteLaboratorioResponseDTO resposta(ConviteLaboratorioResumo resumo) {
        return new ConviteLaboratorioResponseDTO(resumo.id(), resumo.laboratorioId(), resumo.emailDestino(),
                resumo.papeis(), resumo.estado(), resumo.expiraEm(), resumo.criadoEm());
    }
}
