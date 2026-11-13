package br.ufla.citogenetica.laboratorio.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.ConviteLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoConviteLaboratorio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConviteLaboratorioRepository extends JpaRepository<ConviteLaboratorio, UUID> {

    Optional<ConviteLaboratorio> findByTokenHash(byte[] tokenHash);

    Optional<ConviteLaboratorio> findByLaboratorioIdAndEmailDestinoAndEstado(
            UUID laboratorioId, String emailDestino, EstadoConviteLaboratorio estado);

    List<ConviteLaboratorio> findAllByLaboratorioIdAndEstadoOrderByCreatedAtDesc(
            UUID laboratorioId, EstadoConviteLaboratorio estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select convite from ConviteLaboratorio convite where convite.id = :id")
    Optional<ConviteLaboratorio> findByIdForUpdate(@Param("id") UUID id);
}
