package br.ufla.citogenetica.contagem.internal.persistence;

import java.util.Optional;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.VersaoProtocolo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VersaoProtocoloRepository extends JpaRepository<VersaoProtocolo, UUID> {

    Optional<VersaoProtocolo> findFirstByProtocoloIdAndPublicadaEmIsNotNullOrderByNumeroDesc(UUID protocoloId);

    Optional<VersaoProtocolo> findByIdAndPublicadaEmIsNotNull(UUID id);
}
