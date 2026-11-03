package br.ufla.citogenetica.usuario.internal.domain;

import br.ufla.citogenetica.shared.persistence.BaseEntity;
import br.ufla.citogenetica.usuario.api.RolesEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "role")
public class Role extends BaseEntity<Long> {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private RolesEnum nome;
}
