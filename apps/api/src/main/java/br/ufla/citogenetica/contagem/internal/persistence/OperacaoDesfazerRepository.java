package br.ufla.citogenetica.contagem.internal.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.OperacaoDesfazer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperacaoDesfazerRepository extends JpaRepository<OperacaoDesfazer, UUID> {

    /** Registro afetado, como foi respondido na primeira execução. */
    interface RegistroAfetado {
        UUID getRegistroId();

        int getQuantidadeRestante();

        boolean getExcluido();
    }

    /**
     * Grava a operação sem abortar a transação quando outra requisição já gravou o mesmo {@code id}. Sem
     * {@code clearAutomatically}: o chamador mantém os registros e a lâmina carregados.
     *
     * @return 1 se inseriu; 0 se o id já existia (o chamador relê por {@code id} para decidir o que responder)
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO operacao_desfazer (id, version, created_at, last_modified_at, lamina_id, hash_conteudo,
                                           quantidade_desfeita)
            VALUES (:id, 0, :agora, :agora, :laminaId, :hash, :quantidade)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int inserirSeAusente(
            @Param("id") UUID id, @Param("agora") Instant agora, @Param("laminaId") UUID laminaId,
            @Param("hash") String hash, @Param("quantidade") int quantidade);

    @Modifying
    @Query(value = """
            INSERT INTO operacao_desfazer_registro (operacao_id, ordem, registro_id, quantidade_restante, excluido)
            VALUES (:operacaoId, :ordem, :registroId, :quantidadeRestante, :excluido)
            """, nativeQuery = true)
    void inserirRegistroAfetado(
            @Param("operacaoId") UUID operacaoId, @Param("ordem") int ordem, @Param("registroId") UUID registroId,
            @Param("quantidadeRestante") int quantidadeRestante, @Param("excluido") boolean excluido);

    @Query(value = """
            SELECT registro_id AS "registroId", quantidade_restante AS "quantidadeRestante", excluido AS "excluido"
              FROM operacao_desfazer_registro
             WHERE operacao_id = :operacaoId
             ORDER BY ordem
            """, nativeQuery = true)
    List<RegistroAfetado> findRegistrosAfetados(@Param("operacaoId") UUID operacaoId);
}
