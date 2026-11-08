package br.ufla.citogenetica.contagem.internal.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.Lamina;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LaminaRepository extends JpaRepository<Lamina, UUID> {

    /** Avulsas ativas do dono, da mais recente para a mais antiga; o desempate por id mantém a ordem estável. */
    @Query(value = """
            select lamina from Lamina lamina
            where lamina.donoUsuarioId = :donoId and lamina.laboratorioId is null and lamina.excluidaEm is null
            order by lamina.createdAt desc, lamina.id
            """, countQuery = """
            select count(lamina) from Lamina lamina
            where lamina.donoUsuarioId = :donoId and lamina.laboratorioId is null and lamina.excluidaEm is null
            """)
    Page<Lamina> findAvulsasAtivas(@Param("donoId") Long donoId, Pageable pageable);

    /** Donos distintos com lâmina ativa no laboratório: base para filtrar os que o leitor pode ver. */
    @Query("""
            select distinct lamina.donoUsuarioId from Lamina lamina
            where lamina.laboratorioId = :laboratorioId and lamina.excluidaEm is null
            """)
    List<Long> findDonosComLaminaAtiva(@Param("laboratorioId") UUID laboratorioId);

    /** O chamador garante que {@code donoIds} não está vazio. */
    @Query(value = """
            select lamina from Lamina lamina
            where lamina.laboratorioId = :laboratorioId and lamina.donoUsuarioId in :donoIds
              and lamina.excluidaEm is null
            order by lamina.createdAt desc, lamina.id
            """, countQuery = """
            select count(lamina) from Lamina lamina
            where lamina.laboratorioId = :laboratorioId and lamina.donoUsuarioId in :donoIds
              and lamina.excluidaEm is null
            """)
    Page<Lamina> findAtivasDoLaboratorio(
            @Param("laboratorioId") UUID laboratorioId, @Param("donoIds") Collection<Long> donoIds, Pageable pageable);

    /**
     * Insere a lâmina sem abortar a transação quando outra requisição já gravou o mesmo {@code id}.
     *
     * @return 1 se inseriu; 0 se o id já existia (o chamador relê por {@code id} para decidir o que responder)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO lamina (id, version, created_at, last_modified_at, dono_usuario_id, laboratorio_id,
                                protocolo_versao_id, nome, especie, tratamento, observacoes, meta, excluida_em)
            VALUES (:id, 0, :agora, :agora, :donoId, :laboratorioId, :protocoloVersaoId, :nome, :especie,
                    :tratamento, :observacoes, CAST(:meta AS INTEGER), NULL)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int inserirSeAusente(
            @Param("id") UUID id, @Param("agora") Instant agora, @Param("donoId") Long donoId,
            @Param("laboratorioId") UUID laboratorioId, @Param("protocoloVersaoId") UUID protocoloVersaoId,
            @Param("nome") String nome, @Param("especie") String especie, @Param("tratamento") String tratamento,
            @Param("observacoes") String observacoes, @Param("meta") Integer meta);
}
