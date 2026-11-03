package br.ufla.citogenetica.controller;

import br.ufla.citogenetica.filter.CriteriaCreator;
import br.ufla.citogenetica.mapper.UsuarioMapper;
import br.ufla.citogenetica.model.usuario.dto.request.AtualizarMeuPerfilRequestDTO;
import br.ufla.citogenetica.model.usuario.Usuario;
import br.ufla.citogenetica.model.usuario.dto.request.AtualizarSenhaRequestDTO;
import br.ufla.citogenetica.model.usuario.dto.request.AtualizarUsuarioRequestDTO;
import br.ufla.citogenetica.model.usuario.dto.request.UsuarioRequestDTO;
import br.ufla.citogenetica.model.usuario.dto.response.UsuarioResponseDTO;
import br.ufla.citogenetica.model.genericDto.PageResponseDTO;
import br.ufla.citogenetica.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Endpoints para gerenciamento de usuários")
@SecurityRequirement(name = "sessao-cookie")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioMapper usuarioMapper;

    @GetMapping
    @Operation(summary = "Listar usuários", description = "Retorna lista paginada de usuários com filtros opcionais")
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    public ResponseEntity<PageResponseDTO<Usuario, UsuarioResponseDTO>> listarUsuarios(
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        Specification<Usuario> spec = CriteriaCreator.byFilterMap(params, Usuario.class, sortBy, sortDirection);
        Pageable pageable = PageRequest.of(page, size);
        Page<Usuario> pageResult = this.usuarioService.buscarTodos(spec, pageable);
        PageResponseDTO<Usuario, UsuarioResponseDTO> response = new PageResponseDTO<>(pageResult,
                usuarioMapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar usuário por ID", description = "Retorna os dados de um usuário específico")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuário encontrado"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    public ResponseEntity<UsuarioResponseDTO> buscarUsuarioPorId(@PathVariable Long id) {
        Usuario usuario = this.usuarioService.buscarPorId(id);
        return ResponseEntity.status(HttpStatus.OK).body(usuarioMapper.toResponseDTO(usuario));
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Atualizar usuário", description = "Atualiza os dados de um usuário existente. Disponível para administradores.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuário alterado com sucesso"),
            @ApiResponse(responseCode = "403", description = "Acesso negado")
    })
    public ResponseEntity<UsuarioResponseDTO> atualizarUsuario(
            @PathVariable Long id,
            @RequestBody @Valid AtualizarUsuarioRequestDTO requestDTO) {
        Usuario usuarioAtualizado = this.usuarioService.atualizar(id, requestDTO);
        return ResponseEntity.status(HttpStatus.OK).body(usuarioMapper.toResponseDTO(usuarioAtualizado));
    }

    @PatchMapping("/{id}")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Trocar status do usuário", description = "Ativa ou desativa um usuário")
    @ApiResponse(responseCode = "204", description = "Status alterado com sucesso")
    public ResponseEntity<Void> trocarStatusUsuario(@PathVariable Long id) {
        this.usuarioService.trocarStatus(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PutMapping("/me/senha")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Trocar minha senha", description = "Atualiza a senha do usuário autenticado após validar a senha atual")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Senha atualizada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "409", description = "Senha atual incorreta")
    })
    public ResponseEntity<Void> atualizarMinhaSenha(
            @RequestBody @Valid AtualizarSenhaRequestDTO requestDTO,
            HttpServletRequest request) {
        String sessionIdAtual = request.getSession(false) == null
                ? null
                : request.getSession(false).getId();
        this.usuarioService.atualizarMinhaSenha(requestDTO, sessionIdAtual);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PutMapping("/me")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Atualizar meu perfil", description = "Atualiza o nome do usuário autenticado. A alteração de e-mail é exclusiva de administradores.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Perfil atualizado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado")
    })
    public ResponseEntity<UsuarioResponseDTO> atualizarMeuPerfil(
            @RequestBody @Valid AtualizarMeuPerfilRequestDTO requestDTO) {
        Usuario usuarioAtualizado = this.usuarioService.atualizarMeuPerfil(requestDTO);
        return ResponseEntity.ok(usuarioMapper.toResponseDTO(usuarioAtualizado));
    }

    @GetMapping("/me")
    @Operation(summary = "Buscar meu perfil", description = "Retorna os dados do usuário que está autenticado (logado)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dados do usuário retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    })
    public ResponseEntity<UsuarioResponseDTO> buscarMeuPerfil() {
        Usuario usuarioLogado = this.usuarioService.buscarMeuPerfil();
        UsuarioResponseDTO response = usuarioMapper.toResponseDTO(usuarioLogado);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
