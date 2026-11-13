package br.ufla.citogenetica.contagem.internal.persistence;

import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.FaseProtocolo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaseProtocoloRepository extends JpaRepository<FaseProtocolo, UUID> {

    List<FaseProtocolo> findByVersaoIdOrderByOrdemAsc(UUID versaoId);
}
