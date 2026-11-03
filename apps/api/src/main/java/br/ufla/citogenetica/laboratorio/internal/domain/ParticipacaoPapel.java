package br.ufla.citogenetica.laboratorio.internal.domain;

import java.time.Instant;
import java.util.UUID;

import br.ufla.citogenetica.shared.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "participacao_papel")
@Getter
@NoArgsConstructor
public class ParticipacaoPapel extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participacao_id", nullable = false, updatable = false)
    private Participacao participacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 20, updatable = false)
    private PapelLaboratorio papel;

    @Column(name = "concedido_por_usuario_id", nullable = false, updatable = false)
    private Long concedidoPorUsuarioId;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @Column(name = "revogado_por_usuario_id")
    private Long revogadoPorUsuarioId;

    public ParticipacaoPapel(UUID id, Participacao participacao, PapelLaboratorio papel,
                             Long concedidoPorUsuarioId) {
        super(id);
        this.participacao = participacao;
        this.papel = papel;
        this.concedidoPorUsuarioId = concedidoPorUsuarioId;
    }

    public void revogar(Long operadorId, Instant instante) {
        if (revogadoEm != null) {
            return;
        }
        this.revogadoEm = instante;
        this.revogadoPorUsuarioId = operadorId;
    }
}
