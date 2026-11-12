package br.ufla.citogenetica.contagem.internal.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.RegistroContagem;
import br.ufla.citogenetica.contagem.internal.domain.StatusRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistroContagemRepository extends JpaRepository<RegistroContagem, UUID>, JpaSpecificationExecutor<RegistroContagem> {

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

    /** Total de células de uma lâmina; lâminas sem registro ativo não aparecem no resultado. */
    interface TotalLamina {
        UUID getLaminaId();

        long getTotal();
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

    /** Aumenta a versão de um registro cuja linha não mudou (edição só dos tipos). A lâmina deve estar travada. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE registro_contagem SET version = version + 1, last_modified_at = :agora WHERE id = :id",
            nativeQuery = true)
    void aumentarVersao(@Param("id") UUID id, @Param("agora") Instant agora);

    @Modifying
    @Query(value = "DELETE FROM registro_contagem_tipo WHERE registro_id = :registroId", nativeQuery = true)
    void removerTipos(@Param("registroId") UUID registroId);

    /** Tipo de um registro com o nome, para o histórico mostrar também os tipos personalizados do dono. */
    interface TipoDoRegistro {
        UUID getRegistroId();

        UUID getTipoId();

        String getNome();
    }

    /** Tipos de vários registros numa só consulta (sem N+1), em ordem crescente do UUID dentro de cada registro. */
    @Query(value = """
            SELECT rt.registro_id AS "registroId", t.id AS "tipoId", t.nome AS "nome"
              FROM registro_contagem_tipo rt
              JOIN tipo_anormalidade t ON t.id = rt.tipo_id
             WHERE rt.registro_id IN (:registroIds)
             ORDER BY rt.registro_id, rt.tipo_id
            """, nativeQuery = true)
    List<TipoDoRegistro> findTiposDosRegistros(@Param("registroIds") Collection<UUID> registroIds);

    /**
     * Registros ativos da lâmina com a fase, a situação e exatamente o conjunto de tipos pedido, do mais recente ao
     * mais antigo, até somar {@code quantidade} células. {@code chaveTipos} são os ids em minúsculas, em ordem
     * crescente e separados por vírgula (vazio = nenhum tipo). Se o disponível for menor que o pedido, devolve todos
     * os que combinam. Chamar só com a lâmina travada.
     */
    @Query(value = """
            SELECT c.id
              FROM (SELECT r.id, r.quantidade, r.registrado_em,
                           SUM(r.quantidade) OVER (ORDER BY r.registrado_em DESC, r.id DESC) AS acumulado
                      FROM registro_contagem r
                     WHERE r.lamina_id = :laminaId AND r.fase_id = :faseId AND r.status = :status
                       AND r.excluido_em IS NULL
                       AND COALESCE((SELECT string_agg(CAST(rt.tipo_id AS text), ',' ORDER BY rt.tipo_id)
                                       FROM registro_contagem_tipo rt
                                      WHERE rt.registro_id = r.id), '') = :chaveTipos) c
             WHERE c.acumulado - c.quantidade < :quantidade
             ORDER BY c.acumulado
            """, nativeQuery = true)
    List<UUID> findIdsParaDesfazer(
            @Param("laminaId") UUID laminaId, @Param("faseId") UUID faseId, @Param("status") String status,
            @Param("chaveTipos") String chaveTipos, @Param("quantidade") int quantidade);

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

    /**
     * Total de células (soma de {@code quantidade} dos registros não excluídos) de várias lâminas numa só consulta.
     * Lâminas sem registro ativo ficam de fora; quem chama assume 0. Não chamar com coleção vazia.
     */
    @Query(value = """
            SELECT r.lamina_id AS "laminaId", SUM(r.quantidade) AS "total"
              FROM registro_contagem r
             WHERE r.lamina_id IN (:laminaIds) AND r.excluido_em IS NULL
             GROUP BY r.lamina_id
            """, nativeQuery = true)
    List<TotalLamina> totaisPorLamina(@Param("laminaIds") Collection<UUID> laminaIds);

    /** Registro ativo com o nome da fase, para a exportação. */
    interface RegistroExportado {
        UUID getId();

        Long getVersion();

        UUID getLaminaId();

        String getFaseNome();

        StatusRegistro getStatus();

        int getQuantidade();

        Instant getRegistradoEm();

        Instant getHoraAparelho();

        String getFusoAparelho();
    }

    /**
     * Registros não excluídos de várias lâminas numa só consulta, por lâmina, em ordem de registro e depois id.
     * Quem chama limita o tamanho da lista (e não passa coleção vazia).
     */
    @Query("""
            select r.id as id, r.version as version, r.laminaId as laminaId, f.nome as faseNome,
                   r.status as status, r.quantidade as quantidade, r.registradoEm as registradoEm,
                   r.horaAparelho as horaAparelho, r.fusoAparelho as fusoAparelho
            from RegistroContagem r, FaseProtocolo f
            where f.id = r.faseId and r.laminaId in :laminaIds and r.excluidoEm is null
            order by r.laminaId, r.registradoEm, r.id
            """)
    List<RegistroExportado> findAtivosParaExportacao(@Param("laminaIds") Collection<UUID> laminaIds);

    /** Nome de um tipo de um registro, para a exportação. */
    interface NomeTipoDoRegistro {
        UUID getRegistroId();

        String getNome();
    }

    /**
     * Tipos dos registros não excluídos de várias lâminas numa só consulta. Ordem estável dentro de cada registro:
     * tipos fixos na ordem do protocolo e depois os personalizados por nome normalizado, com o id como desempate.
     * Quem chama limita o tamanho da lista (e não passa coleção vazia).
     */
    @Query(value = """
            SELECT rt.registro_id AS "registroId", t.nome AS "nome"
              FROM registro_contagem_tipo rt
              JOIN registro_contagem r ON r.id = rt.registro_id
              JOIN tipo_anormalidade t ON t.id = rt.tipo_id
             WHERE r.lamina_id IN (:laminaIds) AND r.excluido_em IS NULL
             ORDER BY rt.registro_id, (t.usuario_id IS NOT NULL), t.ordem, t.nome_normalizado, t.id
            """, nativeQuery = true)
    List<NomeTipoDoRegistro> findNomesTiposParaExportacao(@Param("laminaIds") Collection<UUID> laminaIds);
}
