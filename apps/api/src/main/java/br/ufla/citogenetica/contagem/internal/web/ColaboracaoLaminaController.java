package br.ufla.citogenetica.contagem.internal.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.application.ComentarioLaminaService;
import br.ufla.citogenetica.contagem.internal.application.FotoLaminaService;
import br.ufla.citogenetica.usuario.api.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/laminas/{laminaId}")
@RequiredArgsConstructor
@Tag(name = "Fotos e comentários", description = "Fotos e comentários de uma lâmina")
@SecurityRequirement(name = "sessao-cookie")
public class ColaboracaoLaminaController {

    private final FotoLaminaService fotoService;
    private final ComentarioLaminaService comentarioService;

    public record FotoResponseDTO(UUID id, String legenda, int tamanhoBytes, String contentType,
                                  Long enviadaPorUsuarioId, String enviadaPorNome, Instant enviadaEm) {
        static FotoResponseDTO de(FotoLaminaService.Foto foto) {
            return new FotoResponseDTO(foto.id(), foto.legenda(), foto.tamanhoBytes(), foto.contentType(),
                    foto.enviadaPorUsuarioId(), foto.enviadaPorNome(), foto.enviadaEm());
        }
    }

    public record ComentarioResponseDTO(UUID id, Long autorUsuarioId, String autorNome, String texto, Instant criadoEm) {
        static ComentarioResponseDTO de(ComentarioLaminaService.Comentario comentario) {
            return new ComentarioResponseDTO(comentario.id(), comentario.autorUsuarioId(), comentario.autorNome(),
                    comentario.texto(), comentario.criadoEm());
        }
    }

    public record NovoComentarioRequestDTO(String texto) {}

    @GetMapping("/fotos")
    @Operation(summary = "Listar fotos da lâmina", description = "Da mais antiga para a mais nova. Mesma leitura da lâmina.")
    public List<FotoResponseDTO> listarFotos(@PathVariable UUID laminaId, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return fotoService.listar(laminaId, principal.id()).stream().map(FotoResponseDTO::de).toList();
    }

    @PostMapping(value = "/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Enviar foto", description = "JPG, PNG ou WebP de até 10 MB no campo \"arquivo\", com \"legenda\" opcional. Exige escrita na lâmina.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Foto guardada"),
            @ApiResponse(responseCode = "403", description = "Sem permissão de edição"),
            @ApiResponse(responseCode = "404", description = "Lâmina sem acesso"),
            @ApiResponse(responseCode = "413", description = "Arquivo maior que o permitido"),
            @ApiResponse(responseCode = "422", description = "Não é imagem aceita, legenda longa ou limite de fotos"),
            @ApiResponse(responseCode = "503", description = "Armazenamento de arquivos indisponível")
    })
    public ResponseEntity<FotoResponseDTO> enviarFoto(@PathVariable UUID laminaId,
                                                      @RequestParam("arquivo") MultipartFile arquivo,
                                                      @RequestParam(value = "legenda", required = false) String legenda,
                                                      @AuthenticationPrincipal UsuarioPrincipal principal) {
        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FotoResponseDTO.de(fotoService.enviar(laminaId, conteudo, legenda, principal.id())));
    }

    @GetMapping("/fotos/{fotoId}/arquivo")
    @Operation(summary = "Ver a imagem da foto", description = "O conteúdo de uma foto nunca muda, então pode ficar em cache no navegador.")
    public ResponseEntity<byte[]> arquivoDaFoto(@PathVariable UUID laminaId, @PathVariable UUID fotoId,
                                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        FotoLaminaService.Arquivo arquivo = fotoService.arquivo(laminaId, fotoId, principal.id());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arquivo.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate().immutable())
                .body(arquivo.conteudo());
    }

    @DeleteMapping("/fotos/{fotoId}")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Apagar foto", description = "Exige escrita na lâmina.")
    public ResponseEntity<Void> apagarFoto(@PathVariable UUID laminaId, @PathVariable UUID fotoId,
                                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        fotoService.apagar(laminaId, fotoId, principal.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/comentarios")
    @Operation(summary = "Listar comentários da lâmina", description = "Do mais antigo para o mais novo. Mesma leitura da lâmina.")
    public List<ComentarioResponseDTO> listarComentarios(@PathVariable UUID laminaId,
                                                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        return comentarioService.listar(laminaId, principal.id()).stream().map(ComentarioResponseDTO::de).toList();
    }

    @PostMapping("/comentarios")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Comentar na lâmina", description = "Quem vê a lâmina e participa ativamente do laboratório (ativo) pode comentar.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comentário criado"),
            @ApiResponse(responseCode = "403", description = "Laboratório desativado ou sem participação ativa"),
            @ApiResponse(responseCode = "404", description = "Lâmina sem acesso"),
            @ApiResponse(responseCode = "422", description = "Texto vazio, longo demais ou limite de comentários")
    })
    public ResponseEntity<ComentarioResponseDTO> comentar(@PathVariable UUID laminaId,
                                                          @RequestBody NovoComentarioRequestDTO request,
                                                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ComentarioResponseDTO.de(
                comentarioService.comentar(laminaId, request == null ? null : request.texto(), principal.id())));
    }

    @DeleteMapping("/comentarios/{comentarioId}")
    @SecurityRequirement(name = "csrf-token")
    @Operation(summary = "Apagar comentário", description = "Só o autor apaga o próprio comentário.")
    public ResponseEntity<Void> apagarComentario(@PathVariable UUID laminaId, @PathVariable UUID comentarioId,
                                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        comentarioService.apagar(laminaId, comentarioId, principal.id());
        return ResponseEntity.noContent().build();
    }
}
