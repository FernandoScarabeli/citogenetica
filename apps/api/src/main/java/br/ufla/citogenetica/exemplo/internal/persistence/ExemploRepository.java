package br.ufla.citogenetica.exemplo.internal.persistence;

import br.ufla.citogenetica.exemplo.internal.domain.Exemplo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ExemploRepository extends JpaRepository<Exemplo, Long>, JpaSpecificationExecutor<Exemplo> {
    // JpaRepository já fornece: save(), findById(), findAll(), delete(), etc.
    // JpaSpecificationExecutor fornece: findAll(Specification<T> spec, Pageable pageable)
}
