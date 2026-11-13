package br.ufla.citogenetica.laboratorio.internal.persistence;

import java.util.Optional;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.Laboratorio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LaboratorioRepository extends JpaRepository<Laboratorio, UUID>, JpaSpecificationExecutor<Laboratorio> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select laboratorio from Laboratorio laboratorio where laboratorio.id = :id")
    Optional<Laboratorio> findByIdForUpdate(@Param("id") UUID id);
}
