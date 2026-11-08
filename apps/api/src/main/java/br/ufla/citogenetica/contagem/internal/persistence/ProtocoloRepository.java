package br.ufla.citogenetica.contagem.internal.persistence;

import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.Protocolo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProtocoloRepository extends JpaRepository<Protocolo, UUID> {

    List<Protocolo> findAllByOrderByNomeAsc();
}
