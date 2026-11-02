package br.ufla.citogenetica.repository;

import br.ufla.citogenetica.model.notificacao.Notificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    Page<Notificacao> findByLidaFalseOrderByCreatedDateDesc(Pageable pageable);

    Page<Notificacao> findAllByOrderByCreatedDateDesc(Pageable pageable);

    long countByLidaFalse();

    @Modifying
    @Query("UPDATE Notificacao n SET n.lida = true WHERE n.lida = false")
    int marcarTodasComoLidas();

    /**
     * Deleta notificações lidas mais antigas que a data informada.
     * Usado para limpeza automática de notificações antigas.
     */
    @Modifying
    @Query("DELETE FROM Notificacao n WHERE n.lida = true AND n.createdDate < :dataLimite")
    int deletarAntigasLidas(@Param("dataLimite") LocalDateTime dataLimite);
}

