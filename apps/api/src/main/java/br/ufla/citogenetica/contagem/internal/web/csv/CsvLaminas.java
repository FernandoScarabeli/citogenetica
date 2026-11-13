package br.ufla.citogenetica.contagem.internal.web.csv;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminas;
import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminas.LaminaExportada;
import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminas.RegistroExportado;
import br.ufla.citogenetica.contagem.internal.application.ExportacaoResumos;
import br.ufla.citogenetica.contagem.internal.application.ResumoLamina;
import br.ufla.citogenetica.contagem.internal.domain.StatusRegistro;

/** Os dois CSVs de lâminas (resumo por fase e registros), com as mesmas colunas para uma ou várias lâminas. */
public final class CsvLaminas {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ExportacaoLaminas.FUSO_ARQUIVOS);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ExportacaoLaminas.FUSO_ARQUIVOS);
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ExportacaoLaminas.FUSO_ARQUIVOS);
    private static final String CODIGO_MITOSE = "mitose";

    private CsvLaminas() {
    }

    /** Uma linha por (lâmina, fase), na ordem do protocolo, mesmo com zero. Sem linhas de total. */
    public static String resumo(ExportacaoResumos exportacao) {
        EscritorCsv csv = new EscritorCsv().linhaDeTexto("Lâmina", "Espécie", "Tratamento", "Protocolo", "Dono", "Fase",
                "Normais", "Anormais", "Total", "% anormais", "Tipos de anormalidade", "Índice mitótico (%)",
                "Observações");
        for (ExportacaoResumos.LaminaResumida item : exportacao.laminas()) {
            LaminaExportada lamina = item.lamina();
            ResumoLamina resumo = item.resumo();
            boolean mitose = CODIGO_MITOSE.equals(lamina.protocolo().codigo());
            for (ResumoLamina.Fase fase : resumo.fases()) {
                identificacao(csv, lamina).texto(fase.nome())
                        .inteiro(fase.normais()).inteiro(fase.anormais()).inteiro(fase.total())
                        .percentual(fase.percentualAnormais())
                        .texto(fase.tipos().stream()
                                .map(tipo -> tipo.nome() + ": " + tipo.contagem() + " ("
                                        + EscritorCsv.formatarPercentual(tipo.percentual()) + "%)")
                                .collect(Collectors.joining(", ")));
                if (mitose) {
                    csv.percentual(resumo.indiceMitotico());
                } else {
                    csv.vazio();
                }
                csv.texto(lamina.observacoes()).fimDaLinha();
            }
        }
        return csv.toString();
    }

    /** Uma linha por registro não excluído, na ordem recebida (lâmina, registro, id). */
    public static String registros(ExportacaoLaminas exportacao) {
        EscritorCsv csv = new EscritorCsv().linhaDeTexto("Lâmina", "Espécie", "Tratamento", "Protocolo", "Dono", "Data",
                "Hora", "Fase", "Situação", "Tipos de anormalidade", "Quantidade", "Hora do aparelho", "Registro");
        for (LaminaExportada lamina : exportacao.laminas()) {
            for (RegistroExportado registro : lamina.registros()) {
                identificacao(csv, lamina)
                        .texto(DATA.format(registro.registradoEm())).texto(HORA.format(registro.registradoEm()))
                        .texto(registro.fase())
                        .texto(registro.status() == StatusRegistro.NORMAL ? "Normal" : "Anormal")
                        .texto(String.join(", ", registro.tipos()))
                        .inteiro(registro.quantidade());
                Instant horaAparelho = registro.horaAparelho();
                if (horaAparelho == null) {
                    csv.vazio();
                } else {
                    csv.texto(DATA_HORA.format(horaAparelho));
                }
                csv.texto(registro.id().toString()).fimDaLinha();
            }
        }
        return csv.toString();
    }

    private static EscritorCsv identificacao(EscritorCsv csv, LaminaExportada lamina) {
        return csv.texto(lamina.nome()).texto(lamina.especie()).texto(lamina.tratamento())
                .texto(lamina.protocolo().nome()).texto(lamina.dono().nome());
    }
}
