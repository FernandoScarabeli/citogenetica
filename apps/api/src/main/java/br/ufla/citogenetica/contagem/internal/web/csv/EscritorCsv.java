package br.ufla.citogenetica.contagem.internal.web.csv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Monta um CSV no padrão do Excel em português: BOM, separador ponto e vírgula, fim de linha CRLF, decimais com
 * vírgula e texto sempre entre aspas (aspas internas dobradas). Texto livre é neutralizado contra fórmula. Sem
 * dependência de biblioteca de CSV.
 */
public final class EscritorCsv {

    private static final String BOM = "﻿";
    private static final String SEPARADOR = ";";
    private static final String FIM_DE_LINHA = "\r\n";

    private final StringBuilder saida = new StringBuilder(BOM);
    private final List<String> linha = new ArrayList<>();

    /** Cabeçalho (ou linha) só de texto fixo. */
    public EscritorCsv linhaDeTexto(String... textos) {
        for (String texto : textos) {
            texto(texto);
        }
        return fimDaLinha();
    }

    /** Texto entre aspas; nulo vira texto vazio entre aspas. */
    public EscritorCsv texto(String texto) {
        linha.add('"' + neutralizarFormula(texto == null ? "" : texto).replace("\"", "\"\"") + '"');
        return this;
    }

    public EscritorCsv inteiro(long valor) {
        linha.add(Long.toString(valor));
        return this;
    }

    /** Percentual com 2 casas (HALF_UP) e vírgula decimal, sem aspas, por exemplo {@code 33,33}. */
    public EscritorCsv percentual(double valor) {
        linha.add(formatarPercentual(valor));
        return this;
    }

    /** Célula sem conteúdo (sem aspas). */
    public EscritorCsv vazio() {
        linha.add("");
        return this;
    }

    public EscritorCsv fimDaLinha() {
        saida.append(String.join(SEPARADOR, linha)).append(FIM_DE_LINHA);
        linha.clear();
        return this;
    }

    @Override
    public String toString() {
        return saida.toString();
    }

    /** Duas casas, HALF_UP, vírgula decimal e sem separador de milhar. */
    public static String formatarPercentual(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /**
     * Texto que começa com {@code = + - @}, TAB ou CR vira fórmula no Excel; o apóstrofo na frente faz virar texto
     * (mesma regra de {@code neutralizarFormula} do front).
     */
    static String neutralizarFormula(String texto) {
        if (texto.isEmpty()) {
            return texto;
        }
        return switch (texto.charAt(0)) {
            case '=', '+', '-', '@', '\t', '\r' -> "'" + texto;
            default -> texto;
        };
    }
}
