package br.ufla.citogenetica.contagem.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.ComentarioLamina;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComentarioLaminaRepository extends JpaRepository<ComentarioLamina, UUID> {

    List<ComentarioLamina> findByLaminaIdOrderByCreatedAtAscIdAsc(UUID laminaId);

    Optional<ComentarioLamina> findByIdAndLaminaId(UUID id, UUID laminaId);

    long countByLaminaId(UUID laminaId);
}
