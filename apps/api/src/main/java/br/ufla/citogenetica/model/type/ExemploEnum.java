package br.ufla.citogenetica.model.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public enum ExemploEnum {
    // Definindo os valores do Enum com um 'label' amigável para o front-end
    TIPO_A("Tipo A"),
    TIPO_B("Tipo B"),
    TIPO_C("Tipo C");

    private final String label;

    ExemploEnum(String label) {
        this.label = label;
    }
}
