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
@Table(name = "vinculo_orientacao")
@Getter
@NoArgsConstructor
public class VinculoOrientacao extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratorio_id", nullable = false, updatable = false)
    private Laboratorio laboratorio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orientador_participacao_id", nullable = false, updatable = false)
    private Participacao orientadorParticipacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pesquisador_participacao_id", nullable = false, updatable = false)
    private Participacao pesquisadorParticipacao;

    @Column(name = "orientador_usuario_id", nullable = false, updatable = false)
    private Long orientadorUsuarioId;

    @Column(name = "pesquisador_usuario_id", nullable = false, updatable = false)
    private Long pesquisadorUsuarioId;

    @Column(name = "criado_por_usuario_id", nullable = false, updatable = false)
    private Long criadoPorUsuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoVinculoOrientacao estado;

    @Column(name = "encerrado_em")
    private Instant encerradoEm;

    @Column(name = "encerrado_por_usuario_id")
    private Long encerradoPorUsuarioId;

    public VinculoOrientacao(UUID id, Laboratorio laboratorio, Participacao orientadorParticipacao,
                             Participacao pesquisadorParticipacao, Long criadoPorUsuarioId) {
        super(id);
        this.laboratorio = laboratorio;
        this.orientadorParticipacao = orientadorParticipacao;
        this.pesquisadorParticipacao = pesquisadorParticipacao;
        this.orientadorUsuarioId = orientadorParticipacao.getUsuarioId();
        this.pesquisadorUsuarioId = pesquisadorParticipacao.getUsuarioId();
        this.criadoPorUsuarioId = criadoPorUsuarioId;
        this.estado = EstadoVinculoOrientacao.ATIVO;
    }

    public void encerrar(Long operadorId, Instant instante) {
        if (estado != EstadoVinculoOrientacao.ATIVO) {
            throw new IllegalStateException("Somente vínculo ativo pode ser encerrado.");
        }
        this.estado = EstadoVinculoOrientacao.ENCERRADO;
        this.encerradoEm = instante;
        this.encerradoPorUsuarioId = operadorId;
    }

    @Override
    public String toString() {
        return "VinculoOrientacao[id=" + getId() + ", laboratorioId=" + laboratorio.getId()
                + ", orientadorUsuarioId=" + orientadorUsuarioId
                + ", pesquisadorUsuarioId=" + pesquisadorUsuarioId + ", estado=" + estado + "]";
    }
}
