package br.ufla.citogenetica.laboratorio.internal.web;

import br.ufla.citogenetica.laboratorio.internal.application.OrientacaoService;
import br.ufla.citogenetica.laboratorio.internal.web.dto.request.ConsumirConviteRequestDTO;
import br.ufla.citogenetica.laboratorio.internal.web.dto.response.PreviaConviteResponseDTO;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orientacoes/convites")
@RequiredArgsConstructor
@Validated
@Tag(name = "Aceite de orientação", description = "Aceite ou recusa de convites de orientação recebidos por e-mail")
@SecurityRequirement(name = "sessao-cookie")
public class ConviteOrientacaoConsumoController {

    private final OrientacaoService orientacaoService;

    @PostMapping("/previa")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Ver o convite de orientação antes de entrar",
            description = "Pública: quem tem o link vê o laboratório, o orientador e o e-mail mascarado. O token vai no corpo, nunca na URL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Convite pendente ou expirado"),
            @ApiResponse(responseCode = "400", description = "Convite inválido ou indisponível (resposta genérica)"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas atingido")
    })
    public PreviaConviteResponseDTO previa(@RequestBody @Valid ConsumirConviteRequestDTO request,
                                           @AuthenticationPrincipal UsuarioPrincipal principal,
                                           HttpServletRequest servletRequest) {
        String emailSessao = principal == null ? null : principal.email();
        return PreviaConviteResponseDTO.de(orientacaoService.previa(request.token(), servletRequest.getRemoteAddr(), emailSessao));
    }

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

    @PostMapping("/{conviteId}/aceitar")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Aceitar convite de orientação pelo sino",
            description = "Sem o link do e-mail: a sessão precisa ser da conta ativa e confirmada do e-mail convidado.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Convite aceito e vínculo criado"),
            @ApiResponse(responseCode = "400", description = "Convite inválido ou indisponível (resposta genérica)"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas atingido; a resposta inclui Retry-After")
    })
    public ResponseEntity<Void> aceitarPelaNotificacao(@PathVariable UUID conviteId,
                                                       @AuthenticationPrincipal UsuarioPrincipal principal,
                                                       HttpServletRequest servletRequest) {
        try {
            orientacaoService.aceitarPelaNotificacao(conviteId, principal.id(), principal.email(), servletRequest.getRemoteAddr());
        } catch (OptimisticLockingFailureException exception) {
            throw new BadRequestException("Convite inválido ou indisponível.");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conviteId}/recusar")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Recusar convite de orientação pelo sino",
            description = "Sem o link do e-mail: a sessão precisa ser da conta ativa e confirmada do e-mail convidado.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Convite recusado sem criar participação ou vínculo"),
            @ApiResponse(responseCode = "400", description = "Convite inválido ou indisponível (resposta genérica)"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas atingido; a resposta inclui Retry-After")
    })
    public ResponseEntity<Void> recusarPelaNotificacao(@PathVariable UUID conviteId,
                                                       @AuthenticationPrincipal UsuarioPrincipal principal,
                                                       HttpServletRequest servletRequest) {
        try {
            orientacaoService.recusarPelaNotificacao(conviteId, principal.id(), principal.email(), servletRequest.getRemoteAddr());
        } catch (OptimisticLockingFailureException exception) {
            throw new BadRequestException("Convite inválido ou indisponível.");
        }
        return ResponseEntity.noContent().build();
    }
}
