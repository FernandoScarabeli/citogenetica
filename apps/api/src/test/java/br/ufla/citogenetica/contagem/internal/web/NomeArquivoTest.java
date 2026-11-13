package br.ufla.citogenetica.contagem.internal.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NomeArquivoTest {

    @Test
    void caractereInvalidoViraEspacoESemGrudarPalavras() {
        assertThat(NomeArquivo.limpar("Controle: L1/água")).isEqualTo("Controle L1 água");
        assertThat(NomeArquivo.limpar("a\\b*c?d\"e<f>g|h")).isEqualTo("a b c d e f g h");
    }

    @Test
    void juntaEspacosETiraPontasEControles() {
        assertThat(NomeArquivo.limpar("  ..Lâmina \t\n 1.. ")).isEqualTo("Lâmina 1");
        assertThat(NomeArquivo.limpar("///")).isEqualTo("sem-nome");
        assertThat(NomeArquivo.limpar("")).isEqualTo("sem-nome");
    }
}
