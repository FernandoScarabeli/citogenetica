package br.ufla.citogenetica.notificacao.internal.persistence;

import br.ufla.citogenetica.notificacao.internal.domain.Notificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Toda consulta recebe o destinatário: não há método que leia ou altere notificações
 * sem filtrar pelo dono.
 */
@Repository
public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    Page<Notificacao> findByDestinatarioIdAndLidaFalseOrderByCreatedDateDesc(Long destinatarioId, Pageable pageable);

    Page<Notificacao> findByDestinatarioIdAndLidaTrueOrderByCreatedDateDesc(Long destinatarioId, Pageable pageable);

    Page<Notificacao> findByDestinatarioIdOrderByCreatedDateDesc(Long destinatarioId, Pageable pageable);

    long countByDestinatarioIdAndLidaFalse(Long destinatarioId);

    Optional<Notificacao> findByIdAndDestinatarioId(Long id, Long destinatarioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Notificacao n
               SET n.lida = true, n.version = n.version + 1, n.lastModifiedDate = CURRENT_TIMESTAMP
             WHERE n.destinatarioId = :destinatarioId AND n.lida = false
            """)
    int marcarTodasComoLidas(@Param("destinatarioId") Long destinatarioId);

    /**
     * Deleta notificações lidas do destinatário mais antigas que a data informada.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM Notificacao n
             WHERE n.destinatarioId = :destinatarioId AND n.lida = true AND n.createdDate < :dataLimite
            """)
    int deletarAntigasLidas(@Param("destinatarioId") Long destinatarioId,
                            @Param("dataLimite") LocalDateTime dataLimite);
}
