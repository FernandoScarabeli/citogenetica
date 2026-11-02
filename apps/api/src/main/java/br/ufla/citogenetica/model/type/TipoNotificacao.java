package br.ufla.citogenetica.model.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public enum TipoNotificacao {
    INFO("Informação"),
    WARNING("Aviso"),
    SUCCESS("Sucesso"),
    ERROR("Erro");

    private final String label;

    TipoNotificacao(String label) {
        this.label = label;
    }
}

