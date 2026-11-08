package br.ufla.citogenetica.contagem;

import br.ufla.citogenetica.contagem.internal.application.NomeTipoAnormalidade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NomeTipoAnormalidadeTest {

    @Test
    void exibicaoColapsaEspacosERemoveAsPontasMantendoCaixaEAcentos() {
        assertThat(NomeTipoAnormalidade.exibicao("  Ponte \t com\n  Fragmento  ")).isEqualTo("Ponte com Fragmento");
        assertThat(NomeTipoAnormalidade.exibicao("Micronúcleo")).isEqualTo("Micronúcleo");
    }

    @Test
    void espacosUnicodeTambemSaoColapsados() {
        assertThat(NomeTipoAnormalidade.exibicao(" Ponte  com  fragmento "))
                .isEqualTo("Ponte com fragmento");
    }

    @ParameterizedTest
    @CsvSource({
            "Micronúcleo, micronucleo",
            "  MICRONÚCLEO  , micronucleo",
            "Cromossomos   não  orientados, cromossomos nao orientados",
            "Núcleos apoptóticos (condensados), nucleos apoptoticos (condensados)",
            "Tétrade, tetrade",
            "Ç Ã Õ Ü, c a o u",
            "Micronúcleo: mais de 3, micronucleo: mais de 3"
    })
    void normalizadoIgnoraEspacosCaixaEAcentos(String entrada, String esperado) {
        assertThat(NomeTipoAnormalidade.normalizado(entrada)).isEqualTo(esperado);
    }

    @Test
    void formasDecompostasEComPostasTemOMesmoNormalizado() {
        assertThat(NomeTipoAnormalidade.normalizado("Micronúcleo"))
                .isEqualTo(NomeTipoAnormalidade.normalizado("Micronúcleo"));
    }

    @Test
    void iMaiusculoComPontoDoTurcoNaoDeixaMarcaResidual() {
        assertThat(NomeTipoAnormalidade.normalizado("İnterfase")).isEqualTo("interfase");
    }

    @Test
    void nomeSoComEspacosOuMarcasFicaVazio() {
        assertThat(NomeTipoAnormalidade.normalizado("   \t")).isEmpty();
        assertThat(NomeTipoAnormalidade.normalizado("́")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u0000", "abc\u0000", "\u200B", "a\u200Bb", "a\u0001b", "\uE000", "\uD800"})
    void detectaCaractereInvisivelOuDeControle(String nome) {
        assertThat(NomeTipoAnormalidade.contemCaractereInvisivelOuDeControle(NomeTipoAnormalidade.exibicao(nome)))
                .isTrue();
    }

    @Test
    void tabulacaoQuebraDeLinhaEEmojiNaoSaoInvisiveis() {
        String exibido = NomeTipoAnormalidade.exibicao("a\tb\nc \uD83D\uDE00");
        assertThat(exibido).isEqualTo("a b c \uD83D\uDE00");
        assertThat(NomeTipoAnormalidade.contemCaractereInvisivelOuDeControle(exibido)).isFalse();
    }
}
