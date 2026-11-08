package br.ufla.citogenetica.contagem.internal.web;

import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.application.CriacaoRegistro;
import br.ufla.citogenetica.contagem.internal.application.NovoRegistro;
import br.ufla.citogenetica.contagem.internal.application.RegistroContagemService;
import br.ufla.citogenetica.contagem.internal.application.ResumoLaminaService;
import br.ufla.citogenetica.contagem.internal.web.dto.request.CriarRegistroContagemRequestDTO;
import br.ufla.citogenetica.contagem.internal.web.dto.response.RegistroContagemResponseDTO;
import br.ufla.citogenetica.contagem.internal.web.dto.response.ResumoLaminaResponseDTO;
import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import br.ufla.citogenetica.usuario.api.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
        List<UUID> tipoIds = request.tipoIds() == null
                ? List.of() : request.tipoIds().stream().map(UUID::fromString).toList();
        CriacaoRegistro criacao = registroService.criar(new NovoRegistro(
                UUID.fromString(request.id()), UUID.fromString(laminaId), UUID.fromString(request.faseId()),
                request.status(), tipoIds, request.quantidade(), request.horaAparelho(), request.fusoAparelho()),
                principal.id());
        return ResponseEntity.status(criacao.criado() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(RegistroContagemResponseDTO.de(criacao.registro()));
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
}
