package br.ufla.citogenetica.contagem.internal.web;

import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.application.CriacaoRegistro;
import br.ufla.citogenetica.contagem.internal.application.DesfazerRegistroService;
import br.ufla.citogenetica.contagem.internal.application.EdicaoRegistro;
import br.ufla.citogenetica.contagem.internal.application.HistoricoRegistroService;
import br.ufla.citogenetica.contagem.internal.application.NovoRegistro;
import br.ufla.citogenetica.contagem.internal.application.PedidoDesfazer;
import br.ufla.citogenetica.contagem.internal.application.RegistroContagemService;
import br.ufla.citogenetica.contagem.internal.application.RegistroHistorico;
import br.ufla.citogenetica.contagem.internal.application.ResumoLaminaService;
import br.ufla.citogenetica.contagem.internal.web.dto.request.AtualizarRegistroContagemRequestDTO;
import br.ufla.citogenetica.contagem.internal.web.dto.request.CriarRegistroContagemRequestDTO;
import br.ufla.citogenetica.contagem.internal.web.dto.request.DesfazerRegistroRequestDTO;
import br.ufla.citogenetica.contagem.internal.web.dto.response.DesfazerRegistroResponseDTO;
import br.ufla.citogenetica.contagem.internal.web.dto.response.RegistroContagemResponseDTO;
import br.ufla.citogenetica.contagem.internal.web.dto.response.RegistroHistoricoResponseDTO;
import br.ufla.citogenetica.contagem.internal.web.dto.response.ResumoLaminaResponseDTO;
import br.ufla.citogenetica.shared.pagination.PageResponseDTO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/laminas/{laminaId}")
@RequiredArgsConstructor
@Validated
@Tag(name = "Registros de contagem", description = "Registros de células contadas e cálculos oficiais da lâmina")
@SecurityRequirement(name = "sessao-cookie")
public class RegistroContagemController {

    private final RegistroContagemService registroService;
    private final ResumoLaminaService resumoService;
    private final HistoricoRegistroService historicoService;
    private final DesfazerRegistroService desfazerService;

    @PostMapping("/registros")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Criar registro de contagem",
            description = "Um registro é um lote de células da mesma fase e situação (lote = 1 registro). "
                    + "Somente o dono da lâmina registra, e só com o laboratório ativo. A hora oficial é a do "
                    + "servidor; hora e fuso do aparelho são informativos. Idempotente pelo id do cliente: repetir o "
                    + "mesmo id, dono e pedido responde 200 com o estado atual do registro, sem efeito e sem "
                    + "ressuscitar registro excluído, mesmo se a lâmina foi excluída ou o acesso mudou. Mesmo id e "
                    + "dono com pedido diferente responde 409. Id de outro dono responde 404, igual a recurso "
                    + "desconhecido.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registro criado"),
            @ApiResponse(responseCode = "200", description = "Repetição idempotente: o registro já existia com o mesmo pedido"),
            @ApiResponse(responseCode = "400", description = "Corpo inválido: situação, tipos, quantidade (inclusive acima do limite "
                    + "vigente), hora ou fuso do aparelho, ou fase e tipos que não pertencem ao protocolo da lâmina"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "403", description = "Sem permissão de edição (não é o dono, ou laboratório desativado)"),
            @ApiResponse(responseCode = "404", description = "Lâmina inexistente, excluída ou sem acesso, ou id de outro dono"),
            @ApiResponse(responseCode = "409", description = "Id já usado com outro pedido")
    })
    public ResponseEntity<RegistroContagemResponseDTO> criar(
            @PathVariable @CanonicalUuidV4 String laminaId,
            @RequestBody @Valid CriarRegistroContagemRequestDTO request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        CriacaoRegistro criacao = registroService.criar(new NovoRegistro(
                UUID.fromString(request.id()), UUID.fromString(laminaId), UUID.fromString(request.faseId()),
                request.status(), uuids(request.tipoIds()), request.quantidade(), request.horaAparelho(), request.fusoAparelho()),
                principal.id());
        return ResponseEntity.status(criacao.criado() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(RegistroContagemResponseDTO.de(criacao.registro()));
    }

    @GetMapping("/registros")
    @Operation(summary = "Listar histórico de registros da lâmina",
            description = "Registros não excluídos, do mais recente ao mais antigo (hora oficial e, no empate, id: a "
                    + "ordem em que o desfazer os consome). Cada item é o registro atual mais os tipos com nome, para "
                    + "que orientador e admin vejam o nome de um tipo personalizado do dono. Filtros opcionais por "
                    + "fase e situação. Leitura igual à da lâmina: avulsa só o dono; de laboratório, o dono, "
                    + "orientadores com vínculo ativo e admins do laboratório. O papel global ADMIN não dá acesso.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de registros retornada"),
            @ApiResponse(responseCode = "400", description = "Paginação, UUID ou situação inválida"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "403", description = "Participa do laboratório, mas não pode ver os dados do dono"),
            @ApiResponse(responseCode = "404", description = "Lâmina inexistente, excluída, avulsa de outra pessoa ou de laboratório sem acesso")
    })
    public ResponseEntity<PageResponseDTO<RegistroHistorico, RegistroHistoricoResponseDTO>> listar(
            @PathVariable @CanonicalUuidV4 String laminaId,
            @Parameter(description = "UUIDv4 canônico de uma fase da lâmina; ausente = todas")
            @RequestParam(required = false) @CanonicalUuidV4 String faseId,
            @Parameter(description = "NORMAL ou ANORMAL; ausente = ambas")
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "a página não pode ser negativa") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "o tamanho da página deve ser no mínimo 1")
            @Max(value = PaginationConstants.MAX_PAGE_SIZE,
                    message = "o tamanho da página deve ser no máximo {value}") int size,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        Page<RegistroHistorico> resultado = historicoService.listar(UUID.fromString(laminaId),
                faseId == null ? null : UUID.fromString(faseId), status, principal.id(), page, size);
        return ResponseEntity.ok(new PageResponseDTO<>(resultado, RegistroHistoricoResponseDTO::de));
    }

    @PatchMapping("/registros/{registroId}")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Editar registro de contagem",
            description = "Substitui fase, situação, tipos e quantidade, com as mesmas validações da criação. Hora "
                    + "oficial, hora e fuso do aparelho não mudam. Toda edição que altere algo, inclusive só os tipos, "
                    + "aumenta a versão; pedido igual ao estado atual não altera nada. Somente o dono edita, e só com "
                    + "o laboratório ativo. Exige a versão esperada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registro atualizado"),
            @ApiResponse(responseCode = "400", description = "Corpo inválido: versão, situação, tipos, quantidade (inclusive acima "
                    + "do limite vigente), ou fase e tipos que não pertencem ao protocolo da lâmina"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "403", description = "Sem permissão de edição (não é o dono, ou laboratório desativado)"),
            @ApiResponse(responseCode = "404", description = "Lâmina ou registro inexistente, excluído, de outra lâmina ou sem acesso"),
            @ApiResponse(responseCode = "409", description = "Versão esperada diferente da atual, ou alteração simultânea")
    })
    public ResponseEntity<RegistroContagemResponseDTO> atualizar(
            @PathVariable @CanonicalUuidV4 String laminaId,
            @PathVariable @CanonicalUuidV4 String registroId,
            @RequestBody @Valid AtualizarRegistroContagemRequestDTO request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ResponseEntity.ok(RegistroContagemResponseDTO.de(registroService.atualizar(new EdicaoRegistro(
                UUID.fromString(laminaId), UUID.fromString(registroId), request.versaoEsperada(),
                UUID.fromString(request.faseId()), request.status(), uuids(request.tipoIds()),
                request.quantidade()), principal.id())));
    }

    @PostMapping("/registros/desfazer")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Desfazer as últimas células de uma contagem",
            description = "Remove a quantidade pedida dos registros não excluídos da lâmina com a mesma fase, a mesma "
                    + "situação e exatamente o mesmo conjunto de tipos (em NORMAL, nenhum), do mais recente ao mais "
                    + "antigo: consome registros inteiros (exclusão lógica) e reduz o último lote se faltar menos que "
                    + "o tamanho dele. Quantidade acima do disponível responde 409 sem alterar nada. Somente o dono "
                    + "desfaz, e só com o laboratório ativo. Idempotente pelo id do cliente: repetir o mesmo id, dono e "
                    + "pedido responde 200 com a resposta original, sem remover de novo, mesmo se a lâmina foi excluída "
                    + "ou o acesso mudou. Mesmo id e dono com pedido diferente responde 409. Id de outro dono responde "
                    + "404, igual a recurso desconhecido.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Células desfeitas, ou repetição idempotente com a resposta original"),
            @ApiResponse(responseCode = "400", description = "Corpo inválido: situação, tipos, quantidade, ou fase e tipos que "
                    + "não pertencem ao protocolo da lâmina"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "403", description = "Sem permissão de edição (não é o dono, ou laboratório desativado)"),
            @ApiResponse(responseCode = "404", description = "Lâmina inexistente, excluída ou sem acesso, ou id de outro dono"),
            @ApiResponse(responseCode = "409", description = "Quantidade acima do disponível, ou id já usado com outro pedido")
    })
    public ResponseEntity<DesfazerRegistroResponseDTO> desfazer(
            @PathVariable @CanonicalUuidV4 String laminaId,
            @RequestBody @Valid DesfazerRegistroRequestDTO request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ResponseEntity.ok(DesfazerRegistroResponseDTO.de(desfazerService.desfazer(new PedidoDesfazer(
                UUID.fromString(request.id()), UUID.fromString(laminaId), UUID.fromString(request.faseId()),
                request.status(), uuids(request.tipoIds()), request.quantidade()), principal.id())));
    }

    @GetMapping("/resumo")
    @Operation(summary = "Ver cálculos oficiais da lâmina",
            description = "Totais, índice mitótico e, por fase, normais, anormais, percentual de anormais e contagem por "
                    + "tipo, somando a quantidade dos registros não excluídos. Percentuais sem arredondamento. "
                    + "Leitura igual à da lâmina: avulsa só o dono; de laboratório, o dono, orientadores com vínculo "
                    + "ativo e admins do laboratório. O papel global ADMIN não dá acesso.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo retornado"),
            @ApiResponse(responseCode = "400", description = "UUID inválido"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "403", description = "Participa do laboratório, mas não pode ver os dados do dono"),
            @ApiResponse(responseCode = "404", description = "Lâmina inexistente, excluída, avulsa de outra pessoa ou de laboratório sem acesso")
    })
    public ResponseEntity<ResumoLaminaResponseDTO> resumo(
            @PathVariable @CanonicalUuidV4 String laminaId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ResponseEntity.ok(ResumoLaminaResponseDTO.de(
                resumoService.resumir(UUID.fromString(laminaId), principal.id())));
    }

    private static List<UUID> uuids(List<String> textos) {
        return textos == null ? List.of() : textos.stream().map(UUID::fromString).toList();
    }
}
