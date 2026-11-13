package br.ufla.citogenetica.contagem.internal.domain;

import java.util.UUID;

import br.ufla.citogenetica.shared.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tipo de anormalidade de uma fase. Sem {@code usuarioId} é tipo fixo da versão (criado por migration);
 * com {@code usuarioId} é tipo personalizado, e {@code laboratorioId} nulo significa lâmina avulsa.
 * A criação de personalizados é feita pelo repositório com {@code INSERT ... ON CONFLICT DO NOTHING}.
 */
@Entity
@Table(name = "tipo_anormalidade")
@Getter
@NoArgsConstructor
public class TipoAnormalidade extends UuidEntity {

    @Column(name = "fase_id", nullable = false, updatable = false)
    private UUID faseId;

    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;

    @Column(name = "laboratorio_id", updatable = false)
    private UUID laboratorioId;

    @Column(name = "nome", nullable = false, length = 120, updatable = false)
    private String nome;

    @Column(name = "nome_normalizado", nullable = false, length = 120, updatable = false)
    private String nomeNormalizado;

    @Column(name = "ordem", updatable = false)
    private Integer ordem;
}
