package br.ufla.citogenetica.contagem.internal.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminaService;
import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminas;
import br.ufla.citogenetica.contagem.internal.application.ExportacaoResumos;
import br.ufla.citogenetica.contagem.internal.web.csv.CsvLaminas;
import br.ufla.citogenetica.contagem.internal.web.dto.response.ExportacaoLaminasResponseDTO;
import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import br.ufla.citogenetica.usuario.api.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

/** Exportação em JSON e CSV, só leitura, com a mesma regra de acesso das rotas de lâmina. */
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
    private static final String CSV = "text/csv";
    private static final MediaType CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

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
        return anexo(exportacao, nomeVarias(null, exportacao.exportadoEm(), EXTENSAO));
    }

    // ------------------------------------------------------------------ CSV (padrão do Excel em português)

    private static final String DESC_CSV = "Arquivo CSV em UTF-8 com BOM, separador ponto e vírgula, fim de linha CRLF e "
            + "decimais com vírgula; datas e horas no horário de Brasília. ";
    private static final String DESC_UMA = "Mesma regra de leitura de GET /laminas/{id}: avulsa somente o dono; de "
            + "laboratório, o dono, orientadores com vínculo ativo e admins do laboratório. O papel global ADMIN não "
            + "dá acesso.";
    private static final String DESC_VARIAS = "Mesma seleção, filtros e ordem de GET /laminas, sem paginação. Sem "
            + "laboratorioId, somente as lâminas avulsas do próprio usuário. Lâminas e registros excluídos não entram.";

    @GetMapping(value = "/{id}/resumo.csv", produces = CSV)
    @Operation(summary = "Exportar o resumo de uma lâmina em CSV",
            description = DESC_CSV + "Uma linha por fase do protocolo (inclusive com zero), com os mesmos números de "
                    + "GET /laminas/{id}/resumo. " + DESC_UMA)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arquivo CSV retornado (Content-Disposition: attachment)",
                    content = @Content(mediaType = CSV, schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "UUID inválido", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Participa do laboratório, mas não pode ver os dados do dono", content = @Content),
            @ApiResponse(responseCode = "404", description = "Lâmina inexistente, excluída, avulsa de outra pessoa ou de laboratório sem acesso", content = @Content)
    })
    public ResponseEntity<String> resumoCsvUma(
            @PathVariable @CanonicalUuidV4 String id,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ExportacaoResumos resumos = service.resumir(UUID.fromString(id), principal.id());
        String nome = PREFIXO_ARQUIVO_UMA + NomeArquivo.limpar(resumos.laminas().get(0).lamina().nome()) + "-resumo.csv";
        return anexoCsv(CsvLaminas.resumo(resumos), nome);
    }

    @GetMapping(value = "/resumo.csv", produces = CSV)
    @Operation(summary = "Exportar o resumo de várias lâminas em CSV",
            description = DESC_CSV + "Uma linha por (lâmina, fase), com os mesmos números de GET /laminas/{id}/resumo. "
                    + DESC_VARIAS + " donoUsuarioId restringe a um dono; se o usuário não puder ver esse dono, o "
                    + "arquivo traz só o cabeçalho.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arquivo CSV retornado (Content-Disposition: attachment)",
                    content = @Content(mediaType = CSV, schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "UUID inválido", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada", content = @Content),
            @ApiResponse(responseCode = "404", description = "Laboratório alheio, inexistente ou do qual o usuário saiu", content = @Content)
    })
    public ResponseEntity<String> resumoCsvVarias(
            @Parameter(description = "UUIDv4 canônico do laboratório; ausente = lâminas avulsas do próprio usuário")
            @RequestParam(required = false) @CanonicalUuidV4 String laboratorioId,
            @Parameter(description = "Id do usuário dono, para filtrar as lâminas de uma pessoa")
            @RequestParam(required = false) Long donoUsuarioId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ExportacaoResumos resumos = service.resumirVarias(
                laboratorioId == null ? null : UUID.fromString(laboratorioId), donoUsuarioId, principal.id());
        return anexoCsv(CsvLaminas.resumo(resumos), nomeVarias("resumo", resumos.exportadoEm(), ".csv"));
    }

    @GetMapping(value = "/{id}/registros.csv", produces = CSV)
    @Operation(summary = "Exportar os registros de uma lâmina em CSV",
            description = DESC_CSV + "Uma linha por registro não excluído, na mesma ordem da exportação JSON. "
                    + DESC_UMA)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arquivo CSV retornado (Content-Disposition: attachment)",
                    content = @Content(mediaType = CSV, schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "UUID inválido", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Participa do laboratório, mas não pode ver os dados do dono", content = @Content),
            @ApiResponse(responseCode = "404", description = "Lâmina inexistente, excluída, avulsa de outra pessoa ou de laboratório sem acesso", content = @Content)
    })
    public ResponseEntity<String> registrosCsvUma(
            @PathVariable @CanonicalUuidV4 String id,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ExportacaoLaminas exportacao = service.exportar(UUID.fromString(id), principal.id());
        String nome = PREFIXO_ARQUIVO_UMA + NomeArquivo.limpar(exportacao.laminas().get(0).nome()) + "-registros.csv";
        return anexoCsv(CsvLaminas.registros(exportacao), nome);
    }

    @GetMapping(value = "/registros.csv", produces = CSV)
    @Operation(summary = "Exportar os registros de várias lâminas em CSV",
            description = DESC_CSV + "Uma linha por registro não excluído, lâminas na ordem da listagem e registros na "
                    + "ordem da exportação JSON. " + DESC_VARIAS + " donoUsuarioId restringe a um dono; se o usuário "
                    + "não puder ver esse dono, o arquivo traz só o cabeçalho.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arquivo CSV retornado (Content-Disposition: attachment)",
                    content = @Content(mediaType = CSV, schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "UUID inválido", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada", content = @Content),
            @ApiResponse(responseCode = "404", description = "Laboratório alheio, inexistente ou do qual o usuário saiu", content = @Content)
    })
    public ResponseEntity<String> registrosCsvVarias(
            @Parameter(description = "UUIDv4 canônico do laboratório; ausente = lâminas avulsas do próprio usuário")
            @RequestParam(required = false) @CanonicalUuidV4 String laboratorioId,
            @Parameter(description = "Id do usuário dono, para filtrar as lâminas de uma pessoa")
            @RequestParam(required = false) Long donoUsuarioId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ExportacaoLaminas exportacao = service.exportarVarias(
                laboratorioId == null ? null : UUID.fromString(laboratorioId), donoUsuarioId, principal.id());
        return anexoCsv(CsvLaminas.registros(exportacao), nomeVarias("registros", exportacao.exportadoEm(), ".csv"));
    }

    /** A data do nome do arquivo é a de Brasília, igual nos três arquivos de várias lâminas. */
    private static String nomeVarias(String conteudo, Instant exportadoEm, String extensao) {
        return PREFIXO_ARQUIVO_VARIAS + (conteudo == null ? "" : conteudo + "-")
                + exportadoEm.atZone(ExportacaoLaminas.FUSO_ARQUIVOS).toLocalDate() + extensao;
    }

    private static ResponseEntity<String> anexoCsv(String corpo, String nomeArquivo) {
        ContentDisposition disposicao = ContentDisposition.attachment()
                .filename(nomeArquivo, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
                .contentType(CSV_UTF8)
                .body(corpo);
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
