package br.ufla.citogenetica.contagem.internal.web;

import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.application.CriacaoTipoPersonalizado;
import br.ufla.citogenetica.contagem.internal.application.TipoAnormalidadePersonalizadoService;
import br.ufla.citogenetica.contagem.internal.application.TipoPersonalizadoResumo;
import br.ufla.citogenetica.contagem.internal.web.dto.request.CriarTipoAnormalidadePersonalizadoRequestDTO;
import br.ufla.citogenetica.contagem.internal.web.dto.response.TipoAnormalidadePersonalizadoResponseDTO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tipos-anormalidade/personalizados")
@RequiredArgsConstructor
@Validated
@Tag(name = "Tipos de anormalidade personalizados",
        description = "Tipos de anormalidade criados pelo próprio usuário, por fase, versão e escopo (laboratório ou lâmina avulsa)")
@SecurityRequirement(name = "sessao-cookie")
public class TipoAnormalidadePersonalizadoController {

    private final TipoAnormalidadePersonalizadoService service;

    @GetMapping
    @Operation(summary = "Listar meus tipos personalizados",
            description = "Somente os tipos do próprio usuário na versão e no escopo informados. "
                    + "Sem laboratorioId, o escopo é o de lâminas avulsas. Sem paginação.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tipos retornados"),
            @ApiResponse(responseCode = "400", description = "Parâmetro ausente ou UUID não canônico"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "404", description = "Versão não publicada ou laboratório sem acesso de leitura")
    })
    public ResponseEntity<List<TipoAnormalidadePersonalizadoResponseDTO>> listar(
            @Parameter(description = "UUIDv4 canônico da versão publicada do protocolo", required = true)
            @RequestParam @CanonicalUuidV4 String versaoId,
            @Parameter(description = "UUIDv4 canônico do laboratório; ausente = lâmina avulsa")
            @RequestParam(required = false) @CanonicalUuidV4 String laboratorioId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        List<TipoAnormalidadePersonalizadoResponseDTO> resposta = service
                .listar(UUID.fromString(versaoId), uuidOuNulo(laboratorioId), principal.id()).stream()
                .map(TipoAnormalidadePersonalizadoController::resposta)
                .toList();
        return ResponseEntity.ok(resposta);
    }

    @PostMapping
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Criar tipo personalizado",
            description = "Idempotente pelo id do cliente: repetir o mesmo id, dono e conteúdo responde 200 sem efeito; "
                    + "id de outro dono responde 404, igual a recurso desconhecido. "
                    + "Nome duplicado (ignorando espaços, caixa e acentos) na fase ou no escopo responde 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tipo criado"),
            @ApiResponse(responseCode = "200", description = "Repetição idempotente: o tipo já existia com o mesmo conteúdo"),
            @ApiResponse(responseCode = "400", description = "Corpo inválido ou nome vazio"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "403", description = "Sem permissão de edição no laboratório (por exemplo, laboratório desativado)"),
            @ApiResponse(responseCode = "404", description = "Fase inexistente ou de versão não publicada, laboratório sem acesso ou id de outro dono"),
            @ApiResponse(responseCode = "409", description = "Nome já existe na fase ou id já usado com outro conteúdo")
    })
    public ResponseEntity<TipoAnormalidadePersonalizadoResponseDTO> criar(
            @RequestBody @Valid CriarTipoAnormalidadePersonalizadoRequestDTO request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        CriacaoTipoPersonalizado criacao = service.criar(
                UUID.fromString(request.id()), UUID.fromString(request.faseId()),
                uuidOuNulo(request.laboratorioId()), request.nome(), principal.id());
        return ResponseEntity.status(criacao.criado() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(resposta(criacao.tipo()));
    }

    private static UUID uuidOuNulo(String valor) {
        return valor == null ? null : UUID.fromString(valor);
    }

    private static TipoAnormalidadePersonalizadoResponseDTO resposta(TipoPersonalizadoResumo resumo) {
        return new TipoAnormalidadePersonalizadoResponseDTO(
                resumo.id(), resumo.faseId(), resumo.laboratorioId(), resumo.nome());
    }
}
