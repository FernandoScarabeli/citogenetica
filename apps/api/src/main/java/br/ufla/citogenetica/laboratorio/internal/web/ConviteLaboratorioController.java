package br.ufla.citogenetica.laboratorio.internal.web;

import br.ufla.citogenetica.laboratorio.internal.application.ConviteLaboratorioService;
import br.ufla.citogenetica.laboratorio.internal.web.dto.request.ConsumirConviteRequestDTO;
import br.ufla.citogenetica.laboratorio.internal.web.dto.response.PreviaConviteResponseDTO;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/convites")
@RequiredArgsConstructor
@Validated
@Tag(name = "Convites", description = "Aceite ou recusa de convites recebidos por e-mail")
@SecurityRequirement(name = "sessao-cookie")
public class ConviteLaboratorioController {

    private final ConviteLaboratorioService conviteService;

    @PostMapping("/previa")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Ver o convite antes de entrar",
            description = "Pública: quem tem o link vê o laboratório, quem convidou e o e-mail mascarado. O token vai no corpo, nunca na URL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Convite pendente ou expirado"),
            @ApiResponse(responseCode = "400", description = "Convite inválido ou indisponível (resposta genérica)"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas atingido")
    })
    public PreviaConviteResponseDTO previa(@RequestBody @Valid ConsumirConviteRequestDTO request,
                                           @AuthenticationPrincipal UsuarioPrincipal principal,
                                           HttpServletRequest servletRequest) {
        String emailSessao = principal == null ? null : principal.email();
        return PreviaConviteResponseDTO.de(conviteService.previa(request.token(), servletRequest.getRemoteAddr(), emailSessao));
    }

    @PostMapping("/aceitar")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Aceitar convite", description = "Exige sessão confirmada cujo e-mail corresponda ao destinatário do convite.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Convite aceito e participação criada ou reaproveitada"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "400", description = "Convite inválido, expirado ou indisponível (resposta genérica)"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas atingido")
    })
    public ResponseEntity<Void> aceitar(@RequestBody @Valid ConsumirConviteRequestDTO request,
                                        @AuthenticationPrincipal UsuarioPrincipal principal,
                                        HttpServletRequest servletRequest) {
        conviteService.aceitar(request.token(), principal.id(), principal.email(), servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recusar")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Recusar convite", description = "Somente o usuário autenticado no e-mail destinatário pode recusar.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Convite recusado"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "400", description = "Convite inválido, expirado ou indisponível (resposta genérica)"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas atingido")
    })
    public ResponseEntity<Void> recusar(@RequestBody @Valid ConsumirConviteRequestDTO request,
                                        @AuthenticationPrincipal UsuarioPrincipal principal,
                                        HttpServletRequest servletRequest) {
        conviteService.recusar(request.token(), principal.id(), principal.email(), servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
