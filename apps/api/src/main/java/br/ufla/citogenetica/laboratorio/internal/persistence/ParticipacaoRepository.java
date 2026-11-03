package br.ufla.citogenetica.laboratorio.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.EstadoParticipacao;
import br.ufla.citogenetica.laboratorio.internal.domain.Participacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipacaoRepository extends JpaRepository<Participacao, UUID> {

    Optional<Participacao> findByLaboratorioIdAndUsuarioIdAndEstado(
            UUID laboratorioId, Long usuarioId, EstadoParticipacao estado);

    boolean existsByLaboratorioIdAndUsuarioIdAndEstado(
            UUID laboratorioId, Long usuarioId, EstadoParticipacao estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select participacao from Participacao participacao "
            + "where participacao.laboratorio.id = :laboratorioId "
            + "and participacao.usuarioId = :usuarioId and participacao.estado = :estado")
    Optional<Participacao> findByLaboratorioIdAndUsuarioIdAndEstadoForUpdate(
            @Param("laboratorioId") UUID laboratorioId,
            @Param("usuarioId") Long usuarioId,
            @Param("estado") EstadoParticipacao estado);

    Optional<Participacao> findByIdAndLaboratorioIdAndEstado(
            UUID id, UUID laboratorioId, EstadoParticipacao estado);

    List<Participacao> findAllByLaboratorioIdAndEstadoOrderByCreatedAtAsc(
            UUID laboratorioId, EstadoParticipacao estado);

    List<Participacao> findAllByUsuarioIdAndEstado(Long usuarioId, EstadoParticipacao estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select participacao from Participacao participacao "
            + "where participacao.id = :id and participacao.laboratorio.id = :laboratorioId")
    Optional<Participacao> findByIdAndLaboratorioIdForUpdate(
            @Param("id") UUID id, @Param("laboratorioId") UUID laboratorioId);
}
