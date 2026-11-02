package br.ufla.citogenetica.model.roles;

import br.ufla.citogenetica.model.baseEntity.BaseEntity;
import br.ufla.citogenetica.model.type.RolesEnum;
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
