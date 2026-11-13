package br.ufla.citogenetica.usuario.internal.persistence;

import br.ufla.citogenetica.usuario.internal.domain.Role;
import br.ufla.citogenetica.usuario.api.RolesEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    boolean existsByNome(RolesEnum nome);
    Optional<Role> findByNome(RolesEnum nome);
}
