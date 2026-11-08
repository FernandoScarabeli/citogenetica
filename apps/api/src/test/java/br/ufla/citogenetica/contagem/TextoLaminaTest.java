package br.ufla.citogenetica.contagem;

import br.ufla.citogenetica.contagem.internal.application.TextoLamina;
import br.ufla.citogenetica.shared.web.error.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextoLaminaTest {

    @Test
    void nomeColapsaEspacosEAceitaCento20CaracteresDepoisDeNormalizar() {
        assertThat(TextoLamina.nome("  Lâmina \t  01\n")).isEqualTo("Lâmina 01");
        String cento20 = "a".repeat(120);
        assertThat(TextoLamina.nome("  " + cento20 + "   ")).isEqualTo(cento20);
        assertThat(TextoLamina.nome("a ".repeat(60) + "  ")).hasSize(119);
    }

    @Test
    void nomeAusenteVazioOuSoComEspacosEhRecusado() {
        assertThatThrownBy(() -> TextoLamina.nome(null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> TextoLamina.nome("")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> TextoLamina.nome(" \t\n ")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void nomeComMaisDeCento20CaracteresEhRecusadoContandoPontosDeCodigo() {
        assertThatThrownBy(() -> TextoLamina.nome("a".repeat(121))).isInstanceOf(BadRequestException.class);
        assertThat(TextoLamina.nome("🧬".repeat(120))).isNotEmpty();
        assertThatThrownBy(() -> TextoLamina.nome("🧬".repeat(121))).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u0000", "\u0007", "\u007F", "​", "﻿", "‮", "", "͸"})
    void camposCurtosRecusamControleEInvisiveis(String caractere) {
        assertThatThrownBy(() -> TextoLamina.nome("Lâmina" + caractere + "1")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> TextoLamina.especie("Espécie" + caractere)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> TextoLamina.tratamento(caractere)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void especieETratamentoSaoOpcionaisEAusenteOuVazioViraTextoVazio() {
        assertThat(TextoLamina.especie(null)).isEmpty();
        assertThat(TextoLamina.especie("   ")).isEmpty();
        assertThat(TextoLamina.tratamento(null)).isEmpty();
        assertThat(TextoLamina.tratamento("  Colchicina   0,05 % ")).isEqualTo("Colchicina 0,05 %");
        assertThat(TextoLamina.especie("a".repeat(120))).hasSize(120);
        assertThatThrownBy(() -> TextoLamina.especie("a".repeat(121))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> TextoLamina.tratamento("a".repeat(121))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void observacoesMantemQuebrasDeLinhaETabulacaoEAceitamAteCincoMil() {
        assertThat(TextoLamina.observacoes(null)).isEmpty();
        assertThat(TextoLamina.observacoes("  \n ")).isEmpty();
        assertThat(TextoLamina.observacoes("Linha 1\r\nLinha 2\n\tLinha  3 ")).isEqualTo("Linha 1\r\nLinha 2\n\tLinha  3");
        assertThat(TextoLamina.observacoes("a".repeat(5000))).hasSize(5000);
        assertThatThrownBy(() -> TextoLamina.observacoes("a".repeat(5001))).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u0000", "\u0007", "\u001B", "\u007F", "\u0085", "​", "﻿", ""})
    void observacoesRecusamQualquerOutroCaractereDaCategoriaC(String caractere) {
        assertThatThrownBy(() -> TextoLamina.observacoes("antes" + caractere + "depois"))
                .isInstanceOf(BadRequestException.class);
    }
}
