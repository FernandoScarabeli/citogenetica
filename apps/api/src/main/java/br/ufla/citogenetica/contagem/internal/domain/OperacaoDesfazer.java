package br.ufla.citogenetica.contagem.internal.domain;

import java.util.UUID;

import br.ufla.citogenetica.shared.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Operação de desfazer já executada, identificada pelo id do cliente (ADR 0003). Nunca muda depois de gravada: a
 * criação é feita pelo repositório com {@code INSERT ... ON CONFLICT DO NOTHING}, e a resposta original fica em
 * {@code operacao_desfazer_registro}.
 */
@Entity
@Table(name = "operacao_desfazer")
@Getter
@NoArgsConstructor
public class OperacaoDesfazer extends UuidEntity {

    @Column(name = "lamina_id", nullable = false, updatable = false)
    private UUID laminaId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hash_conteudo", nullable = false, length = 64, updatable = false)
    private String hashConteudo;

    @Column(name = "quantidade_desfeita", nullable = false, updatable = false)
    private int quantidadeDesfeita;
}
