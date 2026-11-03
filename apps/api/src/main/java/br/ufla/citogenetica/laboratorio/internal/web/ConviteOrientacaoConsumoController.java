package br.ufla.citogenetica.laboratorio.internal.web;

import br.ufla.citogenetica.laboratorio.internal.application.OrientacaoService;
import br.ufla.citogenetica.laboratorio.internal.web.dto.request.ConsumirConviteRequestDTO;
import br.ufla.citogenetica.shared.web.error.BadRequestException;
import br.ufla.citogenetica.usuario.api.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orientacoes/convites")
@RequiredArgsConstructor
@Validated
@Tag(name = "Aceite de orientação", description = "Aceite ou recusa de convites de orientação recebidos por e-mail")
@SecurityRequirement(name = "sessao-cookie")
public class ConviteOrientacaoConsumoController {

    private final OrientacaoService orientacaoService;

    @PostMapping("/aceitar")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Aceitar convite de orientação",
            description = "Exige sessão ativa com e-mail confirmado igual ao destino normalizado; cria vínculo e participação MEMBRO se necessário.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Convite aceito e vínculo criado"),
            @ApiResponse(responseCode = "400", description = "Convite inválido ou indisponível (resposta genérica)"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas atingido; a resposta inclui Retry-After")
    })
    public ResponseEntity<Void> aceitar(@RequestBody @Valid ConsumirConviteRequestDTO request,
                                        @AuthenticationPrincipal UsuarioPrincipal principal,
                                        HttpServletRequest servletRequest) {
        try {
            orientacaoService.aceitar(request.token(), principal.id(), principal.email(), servletRequest.getRemoteAddr());
        } catch (OptimisticLockingFailureException exception) {
            throw new BadRequestException("Convite inválido ou indisponível.");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recusar")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Recusar convite de orientação",
            description = "Somente a conta ativa e confirmada do e-mail destinatário pode recusar.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Convite recusado sem criar participação ou vínculo"),
            @ApiResponse(responseCode = "400", description = "Convite inválido ou indisponível (resposta genérica)"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas atingido; a resposta inclui Retry-After")
    })
    public ResponseEntity<Void> recusar(@RequestBody @Valid ConsumirConviteRequestDTO request,
                                        @AuthenticationPrincipal UsuarioPrincipal principal,
                                        HttpServletRequest servletRequest) {
        try {
            orientacaoService.recusar(request.token(), principal.id(), principal.email(), servletRequest.getRemoteAddr());
        } catch (OptimisticLockingFailureException exception) {
            throw new BadRequestException("Convite inválido ou indisponível.");
        }
        return ResponseEntity.noContent().build();
    }
}
