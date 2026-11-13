package br.ufla.citogenetica.contagem.internal.web;

import java.util.regex.Pattern;

/** Torna um texto livre (nome da lâmina) seguro para nome de arquivo. */
final class NomeArquivo {

    private static final Pattern INVALIDOS = Pattern.compile("[/\\\\:*?\"<>|\\p{Cc}]");
    private static final Pattern ESPACOS = Pattern.compile("\\s+");
    private static final String PADRAO = "sem-nome";

    private NomeArquivo() {
    }

    /** Troca caracteres inválidos em arquivo e controles por espaço (não gruda palavras), junta espaços e tira pontos e espaços das pontas. */
    static String limpar(String nome) {
        String limpo = ESPACOS.matcher(INVALIDOS.matcher(nome).replaceAll(" ")).replaceAll(" ")
                .replaceAll("^[ .]+|[ .]+$", "");
        return limpo.isEmpty() ? PADRAO : limpo;
    }
}
