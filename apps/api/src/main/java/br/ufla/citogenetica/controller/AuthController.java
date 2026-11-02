package br.ufla.citogenetica.controller;

import br.ufla.citogenetica.mapper.UsuarioMapper;
import br.ufla.citogenetica.model.usuario.dto.request.AtualizarSenhaRequestDTO;
import br.ufla.citogenetica.model.usuario.dto.request.LoginRequestDTO;
import br.ufla.citogenetica.model.usuario.dto.request.RecuperarSenhaRequestDTO;
import br.ufla.citogenetica.model.usuario.dto.request.RedefinirSenhaRequestDTO;
import br.ufla.citogenetica.model.usuario.dto.request.UsuarioRequestDTO;
import br.ufla.citogenetica.model.usuario.dto.request.ValidarCodigoRequestDTO;
import br.ufla.citogenetica.model.usuario.dto.response.LoginResponseDTO;
import br.ufla.citogenetica.model.usuario.dto.response.UsuarioResponseDTO;
import br.ufla.citogenetica.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Endpoints para autenticação e gerenciamento de senha, login e registro de usuários.")
public class AuthController {

    private final AuthService authService;
    private final UsuarioMapper usuarioMapper;

    @PostMapping("/login")
    @Operation(summary = "Realizar login", description = "Autentica um usuário e retorna um token JWT")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login realizado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
    })
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO requestDTO) {
        LoginResponseDTO response = this.authService.login(requestDTO);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/register")
    @Operation(summary = "Registrar novo usuário", description = "Cria uma nova conta de usuário")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Usuário criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<UsuarioResponseDTO> register(@RequestBody @Valid UsuarioRequestDTO requestDTO) {
        UsuarioResponseDTO response = this.authService.register(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/recuperar-senha")
    @Operation(summary = "Recuperar senha", description = "Envia email com código numérico para redefinição")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Email enviado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Email não encontrado")
    })
    public ResponseEntity<Void> recuperarSenha(@RequestBody @Valid RecuperarSenhaRequestDTO requestDTO) {
        this.authService.recuperarSenha(requestDTO.email());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/redefinir-senha")
    @Operation(summary = "Redefinir senha", description = "Redefine a senha usando o código recebido por email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Senha redefinida com sucesso"),
            @ApiResponse(responseCode = "400", description = "Código inválido ou expirado")
    })
    public ResponseEntity<Void> redefinirSenha(@RequestBody @Valid RedefinirSenhaRequestDTO requestDTO) {
        this.authService.redefinirSenha(requestDTO);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/validar-codigo")
    @Operation(summary = "Validar código", description = "Verifica se o código de recuperação é válido sem alterar a senha")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Código válido"),
            @ApiResponse(responseCode = "400", description = "Código inválido, expirado ou inexistente"),
            @ApiResponse(responseCode = "404", description = "Email não encontrado")
    })
    public ResponseEntity<Void> validarCodigo(@RequestBody @Valid ValidarCodigoRequestDTO requestDTO) {
        this.authService.validarCodigo(requestDTO);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
