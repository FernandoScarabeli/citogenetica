package br.ufla.citogenetica.laboratorio.internal.web;

import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.application.LaboratorioService;
import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import br.ufla.citogenetica.usuario.api.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/laboratorios")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administração da plataforma", description = "Ações globais sobre laboratórios")
@SecurityRequirement(name = "sessao-cookie")
public class AdminLaboratorioController {

    private final LaboratorioService laboratorioService;

    @PatchMapping("/{laboratorioId}/desativar")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Desativar laboratório", description = "Bloqueia mutações e mantém leitura para participantes ativos.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Laboratório desativado"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente"),
            @ApiResponse(responseCode = "403", description = "Somente administrador da plataforma"),
            @ApiResponse(responseCode = "404", description = "Laboratório não encontrado")
    })
    public ResponseEntity<Void> desativar(@PathVariable @CanonicalUuidV4 String laboratorioId,
                                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        laboratorioService.desativar(UUID.fromString(laboratorioId), principal.id());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{laboratorioId}/reativar")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Reativar laboratório", description = "Reativa laboratório desativado, após validar novamente o administrador ativo.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Laboratório reativado"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente"),
            @ApiResponse(responseCode = "403", description = "Somente administrador da plataforma"),
            @ApiResponse(responseCode = "404", description = "Laboratório não encontrado"),
            @ApiResponse(responseCode = "409", description = "Laboratório sem administrador ativo")
    })
    public ResponseEntity<Void> reativar(@PathVariable @CanonicalUuidV4 String laboratorioId,
                                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        laboratorioService.reativar(UUID.fromString(laboratorioId), principal.id());
        return ResponseEntity.noContent().build();
    }
}
