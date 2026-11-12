package br.ufla.citogenetica.laboratorio.internal.web;

import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.application.LaboratorioResumo;
import br.ufla.citogenetica.laboratorio.internal.application.LaboratorioService;
import br.ufla.citogenetica.laboratorio.internal.application.ParticipacaoResumo;
import br.ufla.citogenetica.laboratorio.internal.web.dto.request.AlterarPapeisParticipacaoRequestDTO;
import br.ufla.citogenetica.laboratorio.internal.web.dto.request.SalvarLaboratorioRequestDTO;
import br.ufla.citogenetica.laboratorio.internal.web.dto.response.LaboratorioResponseDTO;
import br.ufla.citogenetica.laboratorio.internal.web.dto.response.ParticipacaoResponseDTO;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/laboratorios")
@RequiredArgsConstructor
@Validated
@Tag(name = "Laboratórios", description = "Criação e administração de laboratórios e participações")
@SecurityRequirement(name = "sessao-cookie")
public class LaboratorioController {

    private final LaboratorioService laboratorioService;

    @PostMapping
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Criar laboratório", description = "O usuário confirmado que cria torna-se administrador local.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Laboratório criado"),
            @ApiResponse(responseCode = "400", description = "Nome inválido"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada")
    })
    public ResponseEntity<LaboratorioResponseDTO> criar(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestBody @Valid SalvarLaboratorioRequestDTO request) {
        LaboratorioResumo resumo = laboratorioService.criar(request.nome(), principal.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta(resumo));
    }

    @GetMapping
    @Operation(summary = "Listar meus laboratórios", description = "Retorna somente laboratórios com participação ativa do usuário.")
    @ApiResponse(responseCode = "200", description = "Laboratórios retornados")
    public ResponseEntity<List<LaboratorioResponseDTO>> listarMeus(
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ResponseEntity.ok(laboratorioService.listarMeus(principal.id()).stream()
                .map(LaboratorioController::resposta)
                .toList());
    }

    @GetMapping("/{laboratorioId}")
    @Operation(summary = "Buscar laboratório", description = "Laboratório inexistente e laboratório sem participação ativa respondem igualmente com 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Laboratório retornado"),
            @ApiResponse(responseCode = "404", description = "Laboratório não encontrado")
    })
    public ResponseEntity<LaboratorioResponseDTO> buscar(
            @Parameter(description = "UUIDv4 canônico do laboratório")
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ResponseEntity.ok(resposta(laboratorioService.buscar(uuid(laboratorioId), principal.id())));
    }

    @PatchMapping("/{laboratorioId}")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Alterar nome do laboratório", description = "Somente administrador local; laboratório alheio responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nome atualizado"),
            @ApiResponse(responseCode = "400", description = "Nome inválido ou UUID não canônico"),
            @ApiResponse(responseCode = "403", description = "Participante sem papel de administrador"),
            @ApiResponse(responseCode = "404", description = "Laboratório não encontrado"),
            @ApiResponse(responseCode = "409", description = "Laboratório desativado")
    })
    public ResponseEntity<LaboratorioResponseDTO> atualizarNome(
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @RequestBody @Valid SalvarLaboratorioRequestDTO request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        LaboratorioResumo resumo = laboratorioService.atualizarNome(
                uuid(laboratorioId), principal.id(), request.nome());
        return ResponseEntity.ok(resposta(resumo));
    }

    @GetMapping("/{laboratorioId}/participacoes")
    @Operation(summary = "Listar participantes", description = "Participantes ativos veem nome, e-mail e papéis dos demais participantes ativos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Participantes retornados"),
            @ApiResponse(responseCode = "404", description = "Laboratório não encontrado")
    })
    public ResponseEntity<List<ParticipacaoResponseDTO>> listarParticipacoes(
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        List<ParticipacaoResponseDTO> resposta = laboratorioService
                .listarParticipacoes(uuid(laboratorioId), principal.id()).stream()
                .map(LaboratorioController::resposta)
                .toList();
        return ResponseEntity.ok(resposta);
    }

    @PatchMapping("/{laboratorioId}/participacoes/{participacaoId}/papeis")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Alterar papéis de participante", description = "Requer a versão atual da participação. MEMBRO é obrigatório.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Papéis atualizados"),
            @ApiResponse(responseCode = "400", description = "UUID ou papéis inválidos"),
            @ApiResponse(responseCode = "403", description = "Participante sem papel de administrador"),
            @ApiResponse(responseCode = "404", description = "Laboratório ou participação não encontrado"),
            @ApiResponse(responseCode = "409", description = "Versão obsoleta, último administrador ou ORIENTADOR com vínculo ativo")
    })
    public ResponseEntity<ParticipacaoResponseDTO> alterarPapeis(
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @PathVariable @CanonicalUuidV4 String participacaoId,
            @RequestBody @Valid AlterarPapeisParticipacaoRequestDTO request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ParticipacaoResumo resumo = laboratorioService.alterarPapeis(
                uuid(laboratorioId), uuid(participacaoId), principal.id(),
                request.versaoEsperada(), request.papeis());
        return ResponseEntity.ok(resposta(resumo));
    }

    @DeleteMapping("/{laboratorioId}/participacoes/me")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Sair do laboratório", description = "A participação é encerrada e os dados históricos permanecem.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Participação encerrada"),
            @ApiResponse(responseCode = "404", description = "Laboratório não encontrado"),
            @ApiResponse(responseCode = "409", description = "O último administrador não pode sair")
    })
    public ResponseEntity<Void> sair(
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        laboratorioService.sair(uuid(laboratorioId), principal.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{laboratorioId}/participacoes/{participacaoId}")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Remover participante", description = "Somente administrador local; os dados históricos permanecem.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Participação removida"),
            @ApiResponse(responseCode = "403", description = "Participante sem papel de administrador"),
            @ApiResponse(responseCode = "404", description = "Laboratório ou participação não encontrado"),
            @ApiResponse(responseCode = "409", description = "O último administrador, ou quem ainda tem orientandos, não pode ser removido")
    })
    public ResponseEntity<Void> remover(
            @PathVariable @CanonicalUuidV4 String laboratorioId,
            @PathVariable @CanonicalUuidV4 String participacaoId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        laboratorioService.remover(uuid(laboratorioId), uuid(participacaoId), principal.id());
        return ResponseEntity.noContent().build();
    }

    private static UUID uuid(String valor) {
        return UUID.fromString(valor);
    }

    private static LaboratorioResponseDTO resposta(LaboratorioResumo resumo) {
        return new LaboratorioResponseDTO(resumo.id(), resumo.nome(), resumo.estado(), resumo.papeis(), resumo.versao());
    }

    private static ParticipacaoResponseDTO resposta(ParticipacaoResumo resumo) {
        return new ParticipacaoResponseDTO(resumo.id(), resumo.nome(), resumo.email(), resumo.papeis(), resumo.versao());
    }
}
