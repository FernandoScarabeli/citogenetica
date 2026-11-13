package br.ufla.citogenetica.contagem.internal.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.TipoAnormalidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TipoAnormalidadeRepository extends JpaRepository<TipoAnormalidade, UUID> {

    @Query("""
            select tipo from TipoAnormalidade tipo, FaseProtocolo fase
            where tipo.faseId = fase.id and fase.versaoId = :versaoId and tipo.usuarioId is null
            order by fase.ordem, tipo.ordem
            """)
    List<TipoAnormalidade> findFixosDaVersao(@Param("versaoId") UUID versaoId);

    @Query("""
            select tipo from TipoAnormalidade tipo, FaseProtocolo fase
            where tipo.faseId = fase.id and fase.versaoId = :versaoId
              and tipo.usuarioId = :usuarioId and tipo.laboratorioId is null
            order by fase.ordem, tipo.nomeNormalizado, tipo.id
            """)
    List<TipoAnormalidade> findPersonalizadosAvulsos(
            @Param("versaoId") UUID versaoId, @Param("usuarioId") Long usuarioId);

    @Query("""
            select tipo from TipoAnormalidade tipo, FaseProtocolo fase
            where tipo.faseId = fase.id and fase.versaoId = :versaoId
              and tipo.usuarioId = :usuarioId and tipo.laboratorioId = :laboratorioId
            order by fase.ordem, tipo.nomeNormalizado, tipo.id
            """)
    List<TipoAnormalidade> findPersonalizadosDoLaboratorio(
            @Param("versaoId") UUID versaoId, @Param("usuarioId") Long usuarioId,
            @Param("laboratorioId") UUID laboratorioId);

    boolean existsByFaseIdAndNomeNormalizadoAndUsuarioIdIsNull(UUID faseId, String nomeNormalizado);

    boolean existsByFaseIdAndUsuarioIdAndLaboratorioIdIsNullAndNomeNormalizado(
            UUID faseId, Long usuarioId, String nomeNormalizado);

    boolean existsByFaseIdAndUsuarioIdAndLaboratorioIdAndNomeNormalizado(
            UUID faseId, Long usuarioId, UUID laboratorioId, String nomeNormalizado);

    /**
     * Insere um tipo personalizado sem abortar a transação quando outra requisição já gravou o mesmo
     * {@code id} ou o mesmo nome no escopo. Sem alvo de conflito, qualquer índice único vale.
     *
     * @return 1 se inseriu; 0 se houve conflito (o chamador relê por {@code id} para decidir o que responder)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO tipo_anormalidade (id, version, created_at, last_modified_at, fase_id, usuario_id,
                                           laboratorio_id, nome, nome_normalizado, ordem)
            VALUES (:id, 0, :agora, :agora, :faseId, :usuarioId, :laboratorioId, :nome, :nomeNormalizado, NULL)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int inserirPersonalizadoSeAusente(
            @Param("id") UUID id, @Param("agora") Instant agora, @Param("faseId") UUID faseId,
            @Param("usuarioId") Long usuarioId, @Param("laboratorioId") UUID laboratorioId,
            @Param("nome") String nome, @Param("nomeNormalizado") String nomeNormalizado);
}
