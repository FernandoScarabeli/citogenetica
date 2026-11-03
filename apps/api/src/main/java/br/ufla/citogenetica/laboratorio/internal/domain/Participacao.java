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
@Table(name = "participacao")
@Getter
@NoArgsConstructor
public class Participacao extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratorio_id", nullable = false, updatable = false)
    private Laboratorio laboratorio;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoParticipacao estado;

    @Column(name = "entrou_em", nullable = false, updatable = false)
    private Instant entrouEm;

    @Column(name = "encerrada_em")
    private Instant encerradaEm;

    @Column(name = "encerrada_por_usuario_id")
    private Long encerradaPorUsuarioId;

    @Column(name = "papeis_atualizados_em")
    private Instant papeisAtualizadosEm;

    public Participacao(UUID id, Laboratorio laboratorio, Long usuarioId, Instant entrouEm) {
        super(id);
        this.laboratorio = laboratorio;
        this.usuarioId = usuarioId;
        this.estado = EstadoParticipacao.ATIVA;
        this.entrouEm = entrouEm;
    }

    public void sair(Long operadorId, Instant instante) {
        encerrar(EstadoParticipacao.SAIU, operadorId, instante);
    }

    public void remover(Long operadorId, Instant instante) {
        encerrar(EstadoParticipacao.REMOVIDA, operadorId, instante);
    }

    public void registrarAlteracaoDePapeis(Instant instante) {
        this.papeisAtualizadosEm = instante;
    }

    private void encerrar(EstadoParticipacao novoEstado, Long operadorId, Instant instante) {
        if (estado != EstadoParticipacao.ATIVA) {
            throw new IllegalStateException("Somente participação ativa pode ser encerrada.");
        }
        this.estado = novoEstado;
        this.encerradaEm = instante;
        this.encerradaPorUsuarioId = operadorId;
    }
}
