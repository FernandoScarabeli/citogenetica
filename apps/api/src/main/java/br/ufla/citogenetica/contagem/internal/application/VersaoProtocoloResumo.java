package br.ufla.citogenetica.contagem.internal.application;

import java.util.List;
import java.util.UUID;

public record VersaoProtocoloResumo(UUID id, int numero, List<Fase> fases) {

    public record Fase(UUID id, String nome, int ordem, boolean contaComoDivisao, List<TipoFixo> tipos) {
    }

    public record TipoFixo(UUID id, String nome) {
    }
}
