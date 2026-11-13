package br.ufla.citogenetica.contagem.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.FotoLamina;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FotoLaminaRepository extends JpaRepository<FotoLamina, UUID> {

    List<FotoLamina> findByLaminaIdOrderByCreatedAtAscIdAsc(UUID laminaId);

    Optional<FotoLamina> findByIdAndLaminaId(UUID id, UUID laminaId);

    long countByLaminaId(UUID laminaId);
}
