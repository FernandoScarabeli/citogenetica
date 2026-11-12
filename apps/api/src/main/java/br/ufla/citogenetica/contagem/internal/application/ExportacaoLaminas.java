package br.ufla.citogenetica.contagem.internal.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.StatusRegistro;

/**
 * Exportação versionada de lâminas com os registros ativos. Qualquer mudança incompatível no formato (campo
 * removido, renomeado ou com outro significado) exige subir {@link #VERSAO}; acrescentar campo novo não exige.
 */
public record ExportacaoLaminas(Instant exportadoEm, List<LaminaExportada> laminas) {

    /** Identifica o tipo de arquivo; nunca muda. */
    public static final String FORMATO = "citogenetica-laminas";

    /** Versão do formato do arquivo. */
    public static final int VERSAO = 1;

    public record LaminaExportada(UUID id, String nome, String especie, String tratamento, String observacoes,
                                  Integer meta, Instant criadaEm, Instant atualizadaEm, long versao,
                                  Dono dono, UUID laboratorioId, Protocolo protocolo,
                                  List<RegistroExportado> registros) {
    }

    public record Dono(Long id, String nome) {
    }

    public record Protocolo(String codigo, String nome, int versao) {
    }

    public record RegistroExportado(UUID id, String fase, StatusRegistro status, List<String> tipos, int quantidade,
                                    Instant registradoEm, Instant horaAparelho, String fusoAparelho, long versao) {
    }
}
