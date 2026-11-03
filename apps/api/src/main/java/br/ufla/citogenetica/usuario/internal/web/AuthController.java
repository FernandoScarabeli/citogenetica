package br.ufla.citogenetica.usuario.internal.web;

import br.ufla.citogenetica.usuario.internal.mapper.UsuarioMapper;
import br.ufla.citogenetica.usuario.api.error.RecoveryChallengeException;
import br.ufla.citogenetica.usuario.internal.web.dto.request.AtualizarSenhaRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.LoginRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.RecuperarSenhaRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.RedefinirSenhaRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.UsuarioRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.ValidarCodigoRequestDTO;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.api.UsuarioPrincipal;
import br.ufla.citogenetica.usuario.internal.web.dto.response.UsuarioResponseDTO;
import br.ufla.citogenetica.usuario.internal.application.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Endpoints para autenticação e gerenciamento de senha, login e registro de usuários.")
public class AuthController {

    private final AuthService authService;
    private final UsuarioMapper usuarioMapper;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @GetMapping("/csrf")
    @Operation(summary = "Obter token CSRF", description = "Cria o cookie XSRF-TOKEN que o SPA deve enviar no cabeçalho X-XSRF-TOKEN antes de operações POST, PUT, PATCH ou DELETE.")
    @ApiResponse(responseCode = "204", description = "Cookie CSRF emitido")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/login")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Realizar login", description = "Autentica o usuário e estabelece uma sessão no cookie HttpOnly CITOGENETICA_SESSION")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Login realizado com sucesso; a sessão é enviada em cookie"),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas atingido")
    })
    public ResponseEntity<Void> login(@RequestBody @Valid LoginRequestDTO requestDTO,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        Authentication authenticated = this.authService.login(requestDTO);
        Usuario usuario = (Usuario) authenticated.getPrincipal();
        UsuarioPrincipal principal = new UsuarioPrincipal(
                usuario.getId(), usuario.getEmail(), usuario.getNome(), usuario.getRole().getNome());
        UsernamePasswordAuthenticationToken sessionAuthentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        sessionAuthentication.setDetails(authenticated.getDetails());

        var session = request.getSession(true);
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal.chaveSessao());
        sessionAuthenticationStrategy.onAuthentication(sessionAuthentication, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(sessionAuthentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/logout")
    @Operation(summary = "Encerrar sessão", description = "Invalida a sessão atual e limpa o cookie CITOGENETICA_SESSION. Exige a sessão autenticada e o cabeçalho X-XSRF-TOKEN.")
    @SecurityRequirement(name = "sessao-cookie")
    @ApiResponse(responseCode = "204", description = "Sessão encerrada")
    public ResponseEntity<Void> logoutDocumentado() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    @SecurityRequirement(name = "sessao-cookie")
    @Operation(summary = "Registrar novo usuário", description = "Cria uma nova conta de usuário. Exige o cabeçalho X-XSRF-TOKEN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Usuário criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<UsuarioResponseDTO> register(@RequestBody @Valid UsuarioRequestDTO requestDTO) {
        UsuarioResponseDTO response = this.authService.register(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/recuperar-senha")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Solicitar recuperação de senha", description = "Registra a solicitação de forma uniforme, sem revelar se o e-mail existe")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Solicitação aceita"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas atingido")
    })
    public ResponseEntity<Void> recuperarSenha(@RequestBody @Valid RecuperarSenhaRequestDTO requestDTO) {
        this.authService.recuperarSenha(requestDTO.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/redefinir-senha")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Redefinir senha", description = "Redefine a senha usando o código recebido por email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Senha redefinida com sucesso"),
            @ApiResponse(responseCode = "400", description = "Código inválido ou expirado"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas atingido")
    })
    public ResponseEntity<Void> redefinirSenha(@RequestBody @Valid RedefinirSenhaRequestDTO requestDTO,
                                               HttpServletRequest request) {
        var usuarioRedefinido = this.authService.redefinirSenha(requestDTO);
        if (usuarioRedefinido.isEmpty()) {
            throw new RecoveryChallengeException();
        }
        HttpSession sessaoAtual = request.getSession(false);
        if (sessaoAtual != null && usuarioRedefinido.get().toString().equals(
                sessaoAtual.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))) {
            sessaoAtual.invalidate();
            SecurityContextHolder.clearContext();
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/validar-codigo")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Validar código", description = "Verifica o código sem revelar se a conta ou solicitação existe")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Código válido"),
            @ApiResponse(responseCode = "400", description = "Código inválido ou expirado"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas atingido")
    })
    public ResponseEntity<Void> validarCodigo(@RequestBody @Valid ValidarCodigoRequestDTO requestDTO) {
        if (!this.authService.validarCodigo(requestDTO)) {
            throw new RecoveryChallengeException();
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
