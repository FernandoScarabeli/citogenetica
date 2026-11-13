package br.ufla.citogenetica.usuario.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public enum RolesEnum {
    ADMIN("Administrador"),
    USER("Usuário");
    
    private final String label;

    RolesEnum(String label) {
        this.label = label;
    }
}
