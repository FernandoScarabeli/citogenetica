package br.ufla.citogenetica.contagem.internal.persistence;

import br.ufla.citogenetica.contagem.internal.domain.ParametroContagem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametroContagemRepository extends JpaRepository<ParametroContagem, Short> {
}
