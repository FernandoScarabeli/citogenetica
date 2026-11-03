package br.ufla.citogenetica.laboratorio.internal.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.ParticipacaoPapel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipacaoPapelRepository extends JpaRepository<ParticipacaoPapel, UUID> {

    List<ParticipacaoPapel> findAllByParticipacaoIdAndRevogadoEmIsNull(UUID participacaoId);

    List<ParticipacaoPapel> findAllByParticipacaoIdInAndRevogadoEmIsNull(Collection<UUID> participacaoIds);

    boolean existsByParticipacaoIdAndPapelAndRevogadoEmIsNull(UUID participacaoId, PapelLaboratorio papel);

    @Query("select count(distinct participacao.id) from Participacao participacao "
            + "join ParticipacaoPapel papel on papel.participacao.id = participacao.id "
            + "where participacao.laboratorio.id = :laboratorioId "
            + "and participacao.estado = br.ufla.citogenetica.laboratorio.internal.domain.EstadoParticipacao.ATIVA "
            + "and papel.papel = :papel and papel.revogadoEm is null")
    long contarParticipacoesAtivasComPapel(@Param("laboratorioId") UUID laboratorioId,
                                           @Param("papel") PapelLaboratorio papel);
}
