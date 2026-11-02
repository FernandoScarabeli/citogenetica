package br.ufla.citogenetica.controller;

import br.ufla.citogenetica.filter.CriteriaCreator;
import br.ufla.citogenetica.mapper.UsuarioMapper;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Endpoints para gerenciamento de usuários")
@SecurityRequirement(name = "bearer-jwt")
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
    @Operation(summary = "Atualizar usuário", description = "Atualiza os dados de um usuário existente")
    @ApiResponse(responseCode = "204", description = "Usuário alterado com sucesso")
    public ResponseEntity<UsuarioResponseDTO> atualizarUsuario(
            @PathVariable Long id,
            @RequestBody @Valid AtualizarUsuarioRequestDTO requestDTO) {
        Usuario usuarioAtualizado = this.usuarioService.atualizar(id, requestDTO);
        return ResponseEntity.status(HttpStatus.OK).body(usuarioMapper.toResponseDTO(usuarioAtualizado));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Trocar status do usuário", description = "Ativa ou desativa um usuário")
    @ApiResponse(responseCode = "204", description = "Status alterado com sucesso")
    public ResponseEntity<Void> trocarStatusUsuario(@PathVariable Long id) {
        this.usuarioService.trocarStatus(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PutMapping("/atualizar-senha/{id}")
    @Operation(summary = "Atualizar senha", description = "Atualiza a senha de um usuário")
    @ApiResponse(responseCode = "204", description = "Senha atualizada com sucesso")
    public ResponseEntity<Void> atualizarSenhaUsuario(
            @PathVariable Long id,
            @RequestBody @Valid AtualizarSenhaRequestDTO requestDTO) {
        this.usuarioService.atualizarSenha(id, requestDTO);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/me")
    @Operation(summary = "Buscar meu perfil", description = "Retorna os dados do usuário que está autenticado (logado)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dados do usuário retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    })
    public ResponseEntity<UsuarioResponseDTO> buscarMeuPerfil(Authentication authentication) {
        Usuario usuarioLogado = (Usuario) authentication.getPrincipal();
        UsuarioResponseDTO response = usuarioMapper.toResponseDTO(usuarioLogado);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
