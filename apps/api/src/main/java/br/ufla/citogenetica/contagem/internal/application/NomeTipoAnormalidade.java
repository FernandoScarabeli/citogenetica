package br.ufla.citogenetica.contagem.internal.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regras únicas de nome de tipo de anormalidade.
 * O nome exibido só tem os espaços colapsados e as pontas removidas. A forma normalizada, usada para
 * detectar duplicidade, também vai para minúsculas (pt-BR) e perde os acentos. É mais rígida que o
 * {@code normalize} do front de propósito. O seed da migration V12 usa a mesma forma.
 */
public final class NomeTipoAnormalidade {

    private static final Pattern ESPACOS = Pattern.compile("[\\s\\p{Z}\\u0085]+");
    private static final Pattern CARACTERE_INVISIVEL_OU_DE_CONTROLE = Pattern.compile("\\p{C}");
    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}+");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private NomeTipoAnormalidade() {
    }

    public static String exibicao(String nome) {
        return ESPACOS.matcher(nome).replaceAll(" ").strip();
    }

    /**
     * Indica se o nome já exibível contém caractere da categoria Unicode C (controle, formato, sub-rogado
     * isolado, uso privado ou não atribuído). Tabulação e quebra de linha não contam: {@link #exibicao}
     * já as colapsa em espaço, então a checagem deve receber o nome exibido.
     */
    public static boolean contemCaractereInvisivelOuDeControle(String nomeExibido) {
        return CARACTERE_INVISIVEL_OU_DE_CONTROLE.matcher(nomeExibido).find();
    }

    public static String normalizado(String nome) {
        String minusculo = exibicao(nome).toLowerCase(PT_BR);
        String decomposto = Normalizer.normalize(minusculo, Normalizer.Form.NFD);
        return MARCAS_DIACRITICAS.matcher(decomposto).replaceAll("");
    }
}
