package br.ufla.citogenetica.repository;

import br.ufla.citogenetica.model.roles.Role;
import br.ufla.citogenetica.model.type.RolesEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    boolean existsByNome(RolesEnum nome);
    Optional<Role> findByNome(RolesEnum nome);
}
