package br.ufla.citogenetica.contagem.internal.web;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Formatação única dos percentuais exportados (CSV e PDF), para que nunca discordem. */
public final class Percentual {

    private Percentual() {
    }

    /** Duas casas, HALF_UP, vírgula decimal e sem separador de milhar, por exemplo {@code 33,33}. */
    public static String formatar(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
