package br.ufla.citogenetica.contagem.internal.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.RegistroContagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistroContagemRepository extends JpaRepository<RegistroContagem, UUID> {

    /** Totais de uma fase da lâmina; fases sem registro ativo vêm zeradas. */
    interface TotalFase {
        UUID getFaseId();

        String getNome();

        int getOrdem();

        Boolean getContaComoDivisao();

        long getNormais();

        long getAnormais();
    }

    /** Soma de {@code quantidade} dos registros ativos de uma fase que têm o tipo. */
    interface TotalTipo {
        UUID getFaseId();

        UUID getTipoId();

        String getNome();

        long getContagem();
    }

    /**
     * Insere o registro sem abortar a transação quando outra requisição já gravou o mesmo {@code id}.
     *
     * @return 1 se inseriu; 0 se o id já existia (o chamador relê por {@code id} para decidir o que responder)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO registro_contagem (id, version, created_at, last_modified_at, lamina_id, fase_id, status,
                                           quantidade, registrado_em, hora_aparelho, fuso_aparelho,
                                           hash_conteudo_criacao, excluido_em)
            VALUES (:id, 0, :agora, :agora, :laminaId, :faseId, :status, :quantidade, :agora, :horaAparelho,
                    :fusoAparelho, :hash, NULL)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int inserirSeAusente(
            @Param("id") UUID id, @Param("agora") Instant agora, @Param("laminaId") UUID laminaId,
            @Param("faseId") UUID faseId, @Param("status") String status, @Param("quantidade") int quantidade,
            @Param("horaAparelho") Instant horaAparelho, @Param("fusoAparelho") String fusoAparelho,
            @Param("hash") String hash);

    @Modifying
    @Query(value = "INSERT INTO registro_contagem_tipo (registro_id, tipo_id) VALUES (:registroId, :tipoId)",
            nativeQuery = true)
    void inserirTipo(@Param("registroId") UUID registroId, @Param("tipoId") UUID tipoId);

    /** Em ordem crescente do UUID, que coincide com a ordem do texto canônico em minúsculas. */
    @Query(value = "SELECT tipo_id FROM registro_contagem_tipo WHERE registro_id = :registroId ORDER BY tipo_id",
            nativeQuery = true)
    List<UUID> findTipoIds(@Param("registroId") UUID registroId);

    /** Uma linha por fase da versão, na ordem do protocolo, somando só registros não excluídos da lâmina. */
    @Query(value = """
            SELECT f.id AS "faseId", f.nome AS "nome", f.ordem AS "ordem",
                   f.conta_como_divisao AS "contaComoDivisao",
                   COALESCE(SUM(r.quantidade) FILTER (WHERE r.status = 'NORMAL'), 0) AS "normais",
                   COALESCE(SUM(r.quantidade) FILTER (WHERE r.status = 'ANORMAL'), 0) AS "anormais"
              FROM protocolo_fase f
              LEFT JOIN registro_contagem r
                     ON r.fase_id = f.id AND r.lamina_id = :laminaId AND r.excluido_em IS NULL
             WHERE f.versao_id = :versaoId
             GROUP BY f.id, f.nome, f.ordem, f.conta_como_divisao
             ORDER BY f.ordem
            """, nativeQuery = true)
    List<TotalFase> totaisPorFase(@Param("laminaId") UUID laminaId, @Param("versaoId") UUID versaoId);

    /**
     * Contagem por tipo e fase, só registros não excluídos. Ordem estável: fases pelo id, tipos fixos na ordem do
     * protocolo e depois os personalizados por nome normalizado, com o id como desempate.
     */
    @Query(value = """
            SELECT r.fase_id AS "faseId", t.id AS "tipoId", t.nome AS "nome", SUM(r.quantidade) AS "contagem"
              FROM registro_contagem r
              JOIN registro_contagem_tipo rt ON rt.registro_id = r.id
              JOIN tipo_anormalidade t ON t.id = rt.tipo_id
             WHERE r.lamina_id = :laminaId AND r.excluido_em IS NULL
             GROUP BY r.fase_id, t.id, t.nome, t.usuario_id, t.ordem, t.nome_normalizado
             ORDER BY r.fase_id, (t.usuario_id IS NOT NULL), t.ordem, t.nome_normalizado, t.id
            """, nativeQuery = true)
    List<TotalTipo> totaisPorTipo(@Param("laminaId") UUID laminaId);
}
