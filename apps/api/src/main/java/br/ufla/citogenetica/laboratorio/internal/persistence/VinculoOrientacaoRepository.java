package br.ufla.citogenetica.laboratorio.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.EstadoVinculoOrientacao;
import br.ufla.citogenetica.laboratorio.internal.domain.VinculoOrientacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VinculoOrientacaoRepository extends JpaRepository<VinculoOrientacao, UUID> {

    boolean existsByLaboratorioIdAndOrientadorUsuarioIdAndPesquisadorUsuarioIdAndEstado(
            UUID laboratorioId, Long orientadorUsuarioId, Long pesquisadorUsuarioId,
            EstadoVinculoOrientacao estado);

    boolean existsByLaboratorioIdAndOrientadorParticipacaoIdAndPesquisadorParticipacaoIdAndEstado(
            UUID laboratorioId, UUID orientadorParticipacaoId, UUID pesquisadorParticipacaoId,
            EstadoVinculoOrientacao estado);

    List<VinculoOrientacao> findAllByLaboratorioIdAndEstadoOrderByCreatedAtDesc(
            UUID laboratorioId, EstadoVinculoOrientacao estado);

    @Query("select vinculo from VinculoOrientacao vinculo where vinculo.laboratorio.id = :laboratorioId "
            + "and vinculo.estado = :estado and (vinculo.orientadorParticipacao.id = :participacaoId "
            + "or vinculo.pesquisadorParticipacao.id = :participacaoId) order by vinculo.createdAt desc")
    List<VinculoOrientacao> listarVisiveisDaParticipacao(
            @Param("laboratorioId") UUID laboratorioId,
            @Param("participacaoId") UUID participacaoId,
            @Param("estado") EstadoVinculoOrientacao estado);

    boolean existsByOrientadorParticipacaoIdAndEstado(UUID orientadorParticipacaoId, EstadoVinculoOrientacao estado);

    @Query("select case when count(vinculo) > 0 then true else false end from VinculoOrientacao vinculo "
            + "where (vinculo.orientadorParticipacao.id = :participacaoId "
            + "or vinculo.pesquisadorParticipacao.id = :participacaoId) and vinculo.estado = :estado")
    boolean existsVinculoAtivoDaParticipacao(
            @Param("participacaoId") UUID participacaoId,
            @Param("estado") EstadoVinculoOrientacao estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select vinculo from VinculoOrientacao vinculo where vinculo.id = :id "
            + "and vinculo.laboratorio.id = :laboratorioId")
    Optional<VinculoOrientacao> findByIdAndLaboratorioIdForUpdate(
            @Param("id") UUID id, @Param("laboratorioId") UUID laboratorioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select vinculo from VinculoOrientacao vinculo where vinculo.laboratorio.id = :laboratorioId "
            + "and vinculo.estado = :estado and (vinculo.orientadorParticipacao.id = :participacaoId "
            + "or vinculo.pesquisadorParticipacao.id = :participacaoId) order by vinculo.id")
    List<VinculoOrientacao> findAllByParticipacaoForUpdate(
            @Param("laboratorioId") UUID laboratorioId,
            @Param("participacaoId") UUID participacaoId,
            @Param("estado") EstadoVinculoOrientacao estado);
}
