package br.ufla.citogenetica.contagem.internal.web.csv;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EscritorCsvTest {

    @Test
    void comecaComBomEUsaPontoEVirgulaECrlf() {
        String csv = new EscritorCsv().linhaDeTexto("A", "B").inteiro(1).texto("x").fimDaLinha().toString();

        assertThat(csv).isEqualTo("﻿\"A\";\"B\"\r\n1;\"x\"\r\n");
        assertThat(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)).startsWith(0xEF, 0xBB, 0xBF);
    }

    @Test
    void dobraAspasEMantemQuebraDeLinhaDentroDasAspas() {
        String csv = new EscritorCsv().texto("diz \"oi\"; tchau\nlinha 2").fimDaLinha().toString();

        assertThat(csv).isEqualTo("﻿\"diz \"\"oi\"\"; tchau\nlinha 2\"\r\n");
    }

    @Test
    void percentualTemDuasCasasHalfUpEVirgula() {
        assertThat(EscritorCsv.formatarPercentual(100.0 / 3)).isEqualTo("33,33");
        assertThat(EscritorCsv.formatarPercentual(66.666666)).isEqualTo("66,67");
        assertThat(EscritorCsv.formatarPercentual(0)).isEqualTo("0,00");
        assertThat(EscritorCsv.formatarPercentual(100)).isEqualTo("100,00");
        assertThat(EscritorCsv.formatarPercentual(0.125)).isEqualTo("0,13");
        assertThat(EscritorCsv.formatarPercentual(1234.5)).isEqualTo("1234,50");
    }

    @Test
    void numerosENulosSemAspas() {
        String csv = new EscritorCsv().inteiro(12345).percentual(5).vazio().texto(null).fimDaLinha().toString();

        assertThat(csv).isEqualTo("﻿12345;5,00;;\"\"\r\n");
    }

    @Test
    void neutralizaTextoQueVirariaFormula() {
        for (String inicio : new String[] {"=", "+", "-", "@", "\t", "\r"}) {
            assertThat(EscritorCsv.neutralizarFormula(inicio + "1+1")).isEqualTo("'" + inicio + "1+1");
        }
        assertThat(EscritorCsv.neutralizarFormula("Normal =1")).isEqualTo("Normal =1");
        assertThat(EscritorCsv.neutralizarFormula("")).isEmpty();
        assertThat(new EscritorCsv().texto("=1+1").fimDaLinha().toString()).isEqualTo("﻿\"'=1+1\"\r\n");
    }
}
