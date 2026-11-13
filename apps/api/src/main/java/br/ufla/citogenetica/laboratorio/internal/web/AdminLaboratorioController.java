package br.ufla.citogenetica.laboratorio.internal.web;

import java.util.Set;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.application.AdminLaboratorioResumo;
import br.ufla.citogenetica.laboratorio.internal.application.LaboratorioService;
import br.ufla.citogenetica.laboratorio.internal.domain.Laboratorio;
import br.ufla.citogenetica.laboratorio.internal.web.dto.response.AdminLaboratorioResponseDTO;
import br.ufla.citogenetica.shared.filter.CamposPermitidos;
import br.ufla.citogenetica.shared.filter.CriteriaCreator;
import br.ufla.citogenetica.shared.pagination.PageResponseDTO;
import br.ufla.citogenetica.shared.pagination.PaginationConstants;
import br.ufla.citogenetica.shared.pagination.PageableFactory;
import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import br.ufla.citogenetica.usuario.api.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/laboratorios")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administração da plataforma", description = "Ações globais sobre laboratórios")
@SecurityRequirement(name = "sessao-cookie")
public class AdminLaboratorioController {

    private static final CamposPermitidos CAMPOS_LISTAGEM = new CamposPermitidos(
            Set.of("nome", "estado"), Set.of("nome", "estado", "id"));

    private final LaboratorioService laboratorioService;

    @GetMapping
    @Operation(summary = "Listar laboratórios da plataforma",
            description = "Retorna laboratórios ativos e desativados com quantidade de membros ativos e somente "
                    + "nome e e-mail dos administradores locais ativos. A busca por nome ignora acentos e caixa. "
                    + "A interface pode usar id e estado para chamar PATCH /api/v1/admin/laboratorios/{id}/desativar "
                    + "quando ATIVO ou PATCH /api/v1/admin/laboratorios/{id}/reativar quando DESATIVADO; "
                    + "essas operações existentes estão documentadas neste controller.",
            parameters = {
                    @Parameter(name = "nome", in = ParameterIn.QUERY,
                            description = "Busca parcial por nome, sem diferenciar acentos ou maiúsculas."),
                    @Parameter(name = "estado", in = ParameterIn.QUERY,
                            description = "Filtra pelo estado do laboratório.",
                            schema = @Schema(allowableValues = {"ATIVO", "DESATIVADO"}))
            })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de laboratórios retornada"),
            @ApiResponse(responseCode = "400", description = "Filtro, ordenação ou paginação inválida"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente"),
            @ApiResponse(responseCode = "403", description = "Somente administrador da plataforma")
    })
    public ResponseEntity<PageResponseDTO<AdminLaboratorioResumo, AdminLaboratorioResponseDTO>> listar(
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "a página não pode ser negativa") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "o tamanho da página deve ser no mínimo 1")
            @Max(value = PaginationConstants.MAX_PAGE_SIZE,
                    message = "o tamanho da página deve ser no máximo {value}") int size,
            @RequestParam(defaultValue = "nome")
            @Parameter(description = "Campo de ordenação.", schema = @Schema(allowableValues = {"nome", "estado", "id"}))
            String sortBy,
            @RequestParam(defaultValue = "asc")
            @Parameter(description = "Direção da ordenação.", schema = @Schema(allowableValues = {"asc", "desc"}))
            String sortDirection) {
        Specification<Laboratorio> filtros = CriteriaCreator.byFilterMap(params, CAMPOS_LISTAGEM);
        Pageable pageable = pageableEstavel(page, size, sortBy, sortDirection);
        Page<AdminLaboratorioResumo> resultado = laboratorioService.listarParaAdmin(filtros, pageable);
        PageResponseDTO<AdminLaboratorioResumo, AdminLaboratorioResponseDTO> resposta =
                new PageResponseDTO<>(resultado, AdminLaboratorioResponseDTO::de);
        return ResponseEntity.ok(resposta);
    }

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

    private Pageable pageableEstavel(int page, int size, String sortBy, String sortDirection) {
        Pageable solicitado = PageableFactory.criar(page, size, sortBy, sortDirection, CAMPOS_LISTAGEM);
        Sort ordenacao = solicitado.getSort();
        if (ordenacao.getOrderFor("id") == null) {
            ordenacao = ordenacao.and(Sort.by(Sort.Order.asc("id")));
        }
        return PageRequest.of(solicitado.getPageNumber(), solicitado.getPageSize(), ordenacao);
    }
}
