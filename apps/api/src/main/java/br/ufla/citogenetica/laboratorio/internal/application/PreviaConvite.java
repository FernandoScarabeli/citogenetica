package br.ufla.citogenetica.laboratorio.internal.application;

import java.util.List;

/**
 * O que quem tem o link do convite pode ver antes de entrar na conta.
 * Em convite de orientação, {@code papeis} vem vazio.
 */
/**
 * {@code paraContaAtual} diz se o convite é para o e-mail da sessão aberta; vem nulo quando não há sessão.
 */
public record PreviaConvite(
        String laboratorioNome,
        String convidadoPorNome,
        String emailMascarado,
        List<String> papeis,
        Estado estado,
        Boolean paraContaAtual) {

    /** Só existem estas duas situações: nos demais casos o link já não vale e a resposta é genérica. */
    public enum Estado {
        PENDENTE,
        EXPIRADO
    }
}
