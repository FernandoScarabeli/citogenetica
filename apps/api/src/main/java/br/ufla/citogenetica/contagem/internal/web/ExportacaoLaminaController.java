package br.ufla.citogenetica.contagem.internal.web;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminaService;
import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminas;
import br.ufla.citogenetica.contagem.internal.web.dto.response.ExportacaoLaminasResponseDTO;
import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import br.ufla.citogenetica.usuario.api.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exportação em JSON, só leitura, com a mesma regra de acesso das rotas de lâmina. */
@RestController
@RequestMapping("/api/v1/laminas")
@RequiredArgsConstructor
@Validated
@Tag(name = "Lâminas", description = "Lâminas de contagem, avulsas (só do dono) ou de um laboratório")
@SecurityRequirement(name = "sessao-cookie")
public class ExportacaoLaminaController {

    private static final String PREFIXO_ARQUIVO_UMA = "lamina-";
    private static final String PREFIXO_ARQUIVO_VARIAS = "laminas-";
    private static final String EXTENSAO = ".json";

    private final ExportacaoLaminaService service;

    @GetMapping(value = "/{id}/exportacao.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Exportar uma lâmina em JSON",
            description = "Baixa um arquivo JSON versionado (formato citogenetica-laminas) com a lâmina e seus registros "
                    + "não excluídos. Mesma regra de leitura de GET /laminas/{id}: avulsa somente o dono; de "
                    + "laboratório, o dono, orientadores com vínculo ativo e admins do laboratório. Laboratório "
                    + "desativado continua exportável. O papel global ADMIN não dá acesso.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arquivo JSON retornado (Content-Disposition: attachment)"),
            @ApiResponse(responseCode = "400", description = "UUID inválido"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "403", description = "Participa do laboratório, mas não pode ver os dados do dono"),
            @ApiResponse(responseCode = "404", description = "Lâmina inexistente, excluída, avulsa de outra pessoa ou de laboratório sem acesso")
    })
    public ResponseEntity<ExportacaoLaminasResponseDTO> exportarUma(
            @PathVariable @CanonicalUuidV4 String id,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ExportacaoLaminas exportacao = service.exportar(UUID.fromString(id), principal.id());
        String nome = PREFIXO_ARQUIVO_UMA + NomeArquivo.limpar(exportacao.laminas().get(0).nome()) + EXTENSAO;
        return anexo(exportacao, nome);
    }

    @GetMapping(value = "/exportacao.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Exportar várias lâminas em JSON",
            description = "Baixa um arquivo JSON versionado (formato citogenetica-laminas) com todas as lâminas que "
                    + "GET /laminas listaria, sem paginação e na mesma ordem (da mais recente para a mais antiga). "
                    + "Sem laboratorioId, somente as lâminas avulsas do próprio usuário. Com laboratorioId, somente "
                    + "as que o usuário pode ver naquele laboratório. donoUsuarioId restringe a um dono; se o "
                    + "usuário não puder ver esse dono, a lista vem vazia. Lâminas e registros excluídos não entram.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arquivo JSON retornado (Content-Disposition: attachment)"),
            @ApiResponse(responseCode = "400", description = "UUID inválido"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "404", description = "Laboratório alheio, inexistente ou do qual o usuário saiu")
    })
    public ResponseEntity<ExportacaoLaminasResponseDTO> exportarVarias(
            @Parameter(description = "UUIDv4 canônico do laboratório; ausente = lâminas avulsas do próprio usuário")
            @RequestParam(required = false) @CanonicalUuidV4 String laboratorioId,
            @Parameter(description = "Id do usuário dono, para filtrar as lâminas de uma pessoa")
            @RequestParam(required = false) Long donoUsuarioId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ExportacaoLaminas exportacao = service.exportarVarias(
                laboratorioId == null ? null : UUID.fromString(laboratorioId), donoUsuarioId, principal.id());
        // Data em UTC: o mesmo instante do campo exportadoEm.
        String nome = PREFIXO_ARQUIVO_VARIAS + exportacao.exportadoEm().atZone(ZoneOffset.UTC).toLocalDate() + EXTENSAO;
        return anexo(exportacao, nome);
    }

    private static ResponseEntity<ExportacaoLaminasResponseDTO> anexo(ExportacaoLaminas exportacao, String nomeArquivo) {
        ContentDisposition disposicao = ContentDisposition.attachment()
                .filename(nomeArquivo, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ExportacaoLaminasResponseDTO.de(exportacao));
    }
}
