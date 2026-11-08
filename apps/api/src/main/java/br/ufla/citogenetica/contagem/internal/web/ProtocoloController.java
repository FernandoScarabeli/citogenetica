package br.ufla.citogenetica.contagem.internal.web;

import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.application.ProtocoloResumo;
import br.ufla.citogenetica.contagem.internal.application.ProtocoloService;
import br.ufla.citogenetica.contagem.internal.application.VersaoProtocoloResumo;
import br.ufla.citogenetica.contagem.internal.web.dto.response.FaseProtocoloResponseDTO;
import br.ufla.citogenetica.contagem.internal.web.dto.response.ProtocoloResponseDTO;
import br.ufla.citogenetica.contagem.internal.web.dto.response.TipoAnormalidadeFixoResponseDTO;
import br.ufla.citogenetica.contagem.internal.web.dto.response.VersaoProtocoloResponseDTO;
import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/protocolos")
@RequiredArgsConstructor
@Validated
@Tag(name = "Protocolos", description = "Protocolos de contagem (meiose e mitose), versões publicadas, fases e tipos de anormalidade fixos")
@SecurityRequirement(name = "sessao-cookie")
public class ProtocoloController {

    private final ProtocoloService protocoloService;

    @GetMapping
    @Operation(summary = "Listar protocolos",
            description = "Cada protocolo com a versão publicada mais recente, suas fases em ordem e os tipos de anormalidade fixos de cada fase.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Protocolos retornados"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada")
    })
    public ResponseEntity<List<ProtocoloResponseDTO>> listar() {
        return ResponseEntity.ok(protocoloService.listar().stream()
                .map(ProtocoloController::resposta)
                .toList());
    }

    @GetMapping("/versoes/{versaoId}")
    @Operation(summary = "Buscar versão publicada de protocolo",
            description = "Versão inexistente ou ainda em rascunho responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Versão retornada"),
            @ApiResponse(responseCode = "400", description = "UUID não canônico"),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou conta não habilitada"),
            @ApiResponse(responseCode = "404", description = "Versão não encontrada")
    })
    public ResponseEntity<VersaoProtocoloResponseDTO> buscarVersao(
            @Parameter(description = "UUIDv4 canônico da versão do protocolo")
            @PathVariable @CanonicalUuidV4 String versaoId) {
        return ResponseEntity.ok(resposta(protocoloService.buscarVersaoPublicada(UUID.fromString(versaoId))));
    }

    private static ProtocoloResponseDTO resposta(ProtocoloResumo resumo) {
        return new ProtocoloResponseDTO(
                resumo.id(), resumo.codigo(), resumo.nome(), resposta(resumo.versaoAtual()));
    }

    private static VersaoProtocoloResponseDTO resposta(VersaoProtocoloResumo resumo) {
        return new VersaoProtocoloResponseDTO(resumo.id(), resumo.numero(), resumo.fases().stream()
                .map(fase -> new FaseProtocoloResponseDTO(fase.id(), fase.nome(), fase.ordem(),
                        fase.contaComoDivisao(), fase.tipos().stream()
                                .map(tipo -> new TipoAnormalidadeFixoResponseDTO(tipo.id(), tipo.nome()))
                                .toList()))
                .toList());
    }
}
