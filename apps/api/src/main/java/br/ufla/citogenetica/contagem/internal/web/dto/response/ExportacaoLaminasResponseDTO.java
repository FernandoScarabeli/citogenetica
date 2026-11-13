package br.ufla.citogenetica.contagem.internal.web.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminas;
import br.ufla.citogenetica.contagem.internal.domain.StatusRegistro;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Arquivo de exportação de lâminas, versionado: mudança incompatível sobe 'versao'")
public record ExportacaoLaminasResponseDTO(
        @Schema(description = "Identifica o tipo de arquivo", example = ExportacaoLaminas.FORMATO) String formato,
        @Schema(description = "Versão do formato do arquivo", example = "1") int versao,
        Instant exportadoEm,
        List<LaminaExportadaDTO> laminas) {

    public static ExportacaoLaminasResponseDTO de(ExportacaoLaminas exportacao) {
        return new ExportacaoLaminasResponseDTO(ExportacaoLaminas.FORMATO, ExportacaoLaminas.VERSAO,
                exportacao.exportadoEm(), exportacao.laminas().stream().map(LaminaExportadaDTO::de).toList());
    }

    public record LaminaExportadaDTO(
            UUID id,
            String nome,
            String especie,
            String tratamento,
            String observacoes,
            @Schema(description = "Meta de células; nula quando não há meta", nullable = true) Integer meta,
            Instant criadaEm,
            Instant atualizadaEm,
            @Schema(description = "Versão da lâmina no momento da exportação") long versao,
            DonoDTO dono,
            @Schema(description = "Nulo quando a lâmina é avulsa", nullable = true) UUID laboratorioId,
            ProtocoloDTO protocolo,
            @Schema(description = "Somente registros não excluídos, por data de registro e depois id")
            List<RegistroExportadoDTO> registros) {

        static LaminaExportadaDTO de(ExportacaoLaminas.LaminaExportada lamina) {
            return new LaminaExportadaDTO(lamina.id(), lamina.nome(), lamina.especie(), lamina.tratamento(),
                    lamina.observacoes(), lamina.meta(), lamina.criadaEm(), lamina.atualizadaEm(), lamina.versao(),
                    new DonoDTO(lamina.dono().id(), lamina.dono().nome()), lamina.laboratorioId(),
                    new ProtocoloDTO(lamina.protocolo().codigo(), lamina.protocolo().nome(),
                            lamina.protocolo().versao()),
                    lamina.registros().stream().map(RegistroExportadoDTO::de).toList());
        }
    }

    public record DonoDTO(Long id, String nome) {
    }

    public record ProtocoloDTO(String codigo, String nome, int versao) {
    }

    public record RegistroExportadoDTO(
            UUID id,
            String fase,
            StatusRegistro status,
            @Schema(description = "Nomes dos tipos de anormalidade: fixos na ordem do protocolo e depois os "
                    + "personalizados por nome") List<String> tipos,
            int quantidade,
            Instant registradoEm,
            @Schema(description = "Hora informada pelo aparelho; nula se não enviada", nullable = true)
            Instant horaAparelho,
            @Schema(description = "Fuso informado pelo aparelho; nulo se não enviado", nullable = true)
            String fusoAparelho,
            @Schema(description = "Versão do registro no momento da exportação") long versao) {

        static RegistroExportadoDTO de(ExportacaoLaminas.RegistroExportado registro) {
            return new RegistroExportadoDTO(registro.id(), registro.fase(), registro.status(), registro.tipos(),
                    registro.quantidade(), registro.registradoEm(), registro.horaAparelho(), registro.fusoAparelho(),
                    registro.versao());
        }
    }
}
