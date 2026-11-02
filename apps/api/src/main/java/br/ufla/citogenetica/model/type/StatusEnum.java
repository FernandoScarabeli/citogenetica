package br.ufla.citogenetica.model.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public enum StatusEnum {
    ATIVO("Ativo"),
    INATIVO("Inativo");
    
    private final String label;

    StatusEnum(String label) {
        this.label = label;
    }
}
