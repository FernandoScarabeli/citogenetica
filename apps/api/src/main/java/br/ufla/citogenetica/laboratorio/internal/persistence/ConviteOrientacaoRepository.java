package br.ufla.citogenetica.laboratorio.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.ConviteOrientacao;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoConviteOrientacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConviteOrientacaoRepository extends JpaRepository<ConviteOrientacao, UUID> {

    Optional<ConviteOrientacao> findByTokenHash(byte[] tokenHash);

    Optional<ConviteOrientacao> findByLaboratorioIdAndOrientadorParticipacaoIdAndEmailDestinoAndEstado(
            UUID laboratorioId, UUID orientadorParticipacaoId, String emailDestino,
            EstadoConviteOrientacao estado);

    List<ConviteOrientacao> findAllByLaboratorioIdAndOrientadorParticipacaoIdAndEstadoOrderByCreatedAtDesc(
            UUID laboratorioId, UUID orientadorParticipacaoId, EstadoConviteOrientacao estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select convite from ConviteOrientacao convite where convite.id = :id")
    Optional<ConviteOrientacao> findByIdForUpdate(@Param("id") UUID id);
}
