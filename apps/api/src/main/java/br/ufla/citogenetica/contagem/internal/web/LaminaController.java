package br.ufla.citogenetica.contagem.internal.web;

import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.application.CriacaoLamina;
import br.ufla.citogenetica.contagem.internal.application.LaminaResumo;
import br.ufla.citogenetica.contagem.internal.application.LaminaService;
import br.ufla.citogenetica.contagem.internal.application.NovaLamina;
import br.ufla.citogenetica.contagem.internal.web.dto.request.AtualizarLaminaRequestDTO;
import br.ufla.citogenetica.contagem.internal.web.dto.request.CriarLaminaRequestDTO;
import br.ufla.citogenetica.contagem.internal.web.dto.response.LaminaResponseDTO;
import br.ufla.citogenetica.shared.pagination.PageResponseDTO;
import br.ufla.citogenetica.shared.pagination.PageableFactory;
import br.ufla.citogenetica.shared.pagination.PaginationConstants;
import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import br.ufla.citogenetica.usuario.api.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/laminas")
@RequiredArgsConstructor
@Validated
@Tag(name = "Lâminas", description = "Lâminas de contagem, avulsas (só do dono) ou de um laboratório")
@SecurityRequirement(name = "sessao-cookie")
public class LaminaController {

    private final LaminaService service;

    @PostMapping
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Criar lâmina",
            description = "Idempotente pelo id do cliente: repetir o mesmo id, dono e conteúdo responde 200 sem efeito, "
                    + "mesmo se a lâmina já foi excluída (ela não volta). O dono é sempre quem está logado. "
                    + "Id de outro dono responde 404, igual a recurso desconhecido. "
                    + "Sem laboratorioId a lâmina é avulsa.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lâmina criada"),
            @ApiResponse(responseCode = "200", description = "Repetição idempotente: a lâmina já existia com o mesmo conteúdo"),
            @ApiResponse(responseCode = "400", description = "Corpo inválido: nome, textos, meta ou UUID"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "403", description = "Sem permissão de edição no laboratório (por exemplo, laboratório desativado)"),
            @ApiResponse(responseCode = "404", description = "Laboratório sem acesso, versão de protocolo inexistente ou em rascunho, ou id de outro dono"),
            @ApiResponse(responseCode = "409", description = "Id já usado com outro conteúdo")
    })
    public ResponseEntity<LaminaResponseDTO> criar(
            @RequestBody @Valid CriarLaminaRequestDTO request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        CriacaoLamina criacao = service.criar(new NovaLamina(
                UUID.fromString(request.id()), UUID.fromString(request.protocoloVersaoId()),
                uuidOuNulo(request.laboratorioId()), request.nome(), request.especie(), request.tratamento(),
                request.observacoes(), request.meta(), request.criadaEm()), principal.id());
        return ResponseEntity.status(criacao.criada() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(LaminaResponseDTO.de(criacao.lamina()));
    }

    @GetMapping
    @Operation(summary = "Listar lâminas",
            description = "Da mais recente para a mais antiga, com desempate por id. Sem laboratorioId, somente as "
                    + "lâminas avulsas do próprio usuário. Com laboratorioId, somente as lâminas que o usuário pode ver "
                    + "naquele laboratório (as suas, as de quem ele orienta com vínculo ativo e, para admins do "
                    + "laboratório, as de todos); as avulsas nunca aparecem. donoUsuarioId restringe a um dono; "
                    + "se o usuário não puder ver esse dono, a página vem vazia. Lâminas excluídas não aparecem.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de lâminas retornada"),
            @ApiResponse(responseCode = "400", description = "Paginação ou UUID inválido"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "404", description = "Laboratório alheio, inexistente ou do qual o usuário saiu")
    })
    public ResponseEntity<PageResponseDTO<LaminaResumo, LaminaResponseDTO>> listar(
            @Parameter(description = "UUIDv4 canônico do laboratório; ausente = lâminas avulsas do próprio usuário")
            @RequestParam(required = false) @CanonicalUuidV4 String laboratorioId,
            @Parameter(description = "Id do usuário dono, para filtrar as lâminas de uma pessoa")
            @RequestParam(required = false) Long donoUsuarioId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "a página não pode ser negativa") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "o tamanho da página deve ser no mínimo 1")
            @Max(value = PaginationConstants.MAX_PAGE_SIZE,
                    message = "o tamanho da página deve ser no máximo {value}") int size,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        Page<LaminaResumo> resultado = service.listar(
                uuidOuNulo(laboratorioId), donoUsuarioId, principal.id(), PageableFactory.criar(page, size));
        return ResponseEntity.ok(new PageResponseDTO<>(resultado, LaminaResponseDTO::de));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver lâmina",
            description = "Avulsa: somente o dono. De laboratório: o dono, orientadores com vínculo ativo e admins do "
                    + "laboratório (que continuam vendo quem já saiu). O papel global ADMIN não dá acesso.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lâmina retornada"),
            @ApiResponse(responseCode = "400", description = "UUID inválido"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "403", description = "Participa do laboratório, mas não pode ver os dados do dono"),
            @ApiResponse(responseCode = "404", description = "Lâmina inexistente, excluída, avulsa de outra pessoa ou de laboratório sem acesso")
    })
    public ResponseEntity<LaminaResponseDTO> buscar(
            @PathVariable @CanonicalUuidV4 String id,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ResponseEntity.ok(LaminaResponseDTO.de(service.buscar(UUID.fromString(id), principal.id())));
    }

    @PatchMapping("/{id}")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Editar lâmina",
            description = "Substitui nome, espécie, tratamento, observações e meta (meta nula remove a meta). "
                    + "Protocolo, laboratório e dono nunca mudam. Somente o dono edita, e só com o laboratório ativo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lâmina atualizada"),
            @ApiResponse(responseCode = "400", description = "Corpo inválido: versão, nome, textos ou meta"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "403", description = "Sem permissão de edição (não é o dono, ou laboratório desativado)"),
            @ApiResponse(responseCode = "404", description = "Lâmina inexistente, excluída ou sem acesso"),
            @ApiResponse(responseCode = "409", description = "Versão esperada diferente da atual, ou alteração simultânea")
    })
    public ResponseEntity<LaminaResponseDTO> atualizar(
            @PathVariable @CanonicalUuidV4 String id,
            @RequestBody @Valid AtualizarLaminaRequestDTO request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        LaminaResumo atualizada = service.atualizar(UUID.fromString(id), request.versaoEsperada(), request.nome(),
                request.especie(), request.tratamento(), request.observacoes(), request.meta(), principal.id());
        return ResponseEntity.ok(LaminaResponseDTO.de(atualizada));
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Excluir lâmina",
            description = "Exclusão lógica, sem restauração. Repetida pelo dono sobre lâmina já excluída, responde 204 "
                    + "sem efeito, seja qual for a versão. Para outra pessoa, lâmina excluída é 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Lâmina excluída (ou já estava excluída)"),
            @ApiResponse(responseCode = "400", description = "UUID inválido ou versão ausente"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "403", description = "Sem permissão de edição (não é o dono, ou laboratório desativado)"),
            @ApiResponse(responseCode = "404", description = "Lâmina inexistente, excluída (para quem não é o dono) ou sem acesso"),
            @ApiResponse(responseCode = "409", description = "Versão esperada diferente da atual, ou alteração simultânea")
    })
    public ResponseEntity<Void> excluir(
            @PathVariable @CanonicalUuidV4 String id,
            @Parameter(description = "Versão da lâmina que o cliente viu", required = true)
            @RequestParam Long versaoEsperada,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        service.excluir(UUID.fromString(id), versaoEsperada, principal.id());
        return ResponseEntity.noContent().build();
    }

    private static UUID uuidOuNulo(String valor) {
        return valor == null ? null : UUID.fromString(valor);
    }
}
