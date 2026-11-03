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
@Table(name = "convite_orientacao")
@Getter
@NoArgsConstructor
public class ConviteOrientacao extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratorio_id", nullable = false, updatable = false)
    private Laboratorio laboratorio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orientador_participacao_id", nullable = false, updatable = false)
    private Participacao orientadorParticipacao;

    @Column(name = "orientador_usuario_id", nullable = false, updatable = false)
    private Long orientadorUsuarioId;

    @Column(name = "email_destino", nullable = false, length = 254, updatable = false)
    private String emailDestino;

    @Column(name = "token_hash", columnDefinition = "bytea", length = 32)
    private byte[] tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoConviteOrientacao estado;

    @Column(name = "expires_at", nullable = false)
    private Instant expiraEm;

    @Column(name = "respondido_em")
    private Instant respondidoEm;

    @Column(name = "respondido_por_usuario_id")
    private Long respondidoPorUsuarioId;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @Column(name = "revogado_por_usuario_id")
    private Long revogadoPorUsuarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vinculo_id")
    private VinculoOrientacao vinculo;

    public ConviteOrientacao(UUID id, Laboratorio laboratorio, Participacao orientadorParticipacao,
                             String emailDestino, byte[] tokenHash, Instant expiraEm) {
        super(id);
        this.laboratorio = laboratorio;
        this.orientadorParticipacao = orientadorParticipacao;
        this.orientadorUsuarioId = orientadorParticipacao.getUsuarioId();
        this.emailDestino = emailDestino;
        this.tokenHash = tokenHash.clone();
        this.estado = EstadoConviteOrientacao.PENDENTE;
        this.expiraEm = expiraEm;
    }

    public void reenviar(byte[] novoTokenHash, Instant novaExpiracao) {
        if (estado != EstadoConviteOrientacao.PENDENTE && estado != EstadoConviteOrientacao.EXPIRADO) {
            throw new IllegalStateException("Somente convite pendente ou expirado pode ser reenviado.");
        }
        this.estado = EstadoConviteOrientacao.PENDENTE;
        this.tokenHash = novoTokenHash.clone();
        this.expiraEm = novaExpiracao;
        this.respondidoEm = null;
        this.respondidoPorUsuarioId = null;
        this.revogadoEm = null;
        this.revogadoPorUsuarioId = null;
        this.vinculo = null;
    }

    public void aceitar(Long usuarioId, VinculoOrientacao novoVinculo, Instant instante) {
        exigirPendente();
        this.estado = EstadoConviteOrientacao.ACEITO;
        this.tokenHash = null;
        this.respondidoEm = instante;
        this.respondidoPorUsuarioId = usuarioId;
        this.vinculo = novoVinculo;
    }

    public void recusar(Long usuarioId, Instant instante) {
        exigirPendente();
        this.estado = EstadoConviteOrientacao.RECUSADO;
        this.tokenHash = null;
        this.respondidoEm = instante;
        this.respondidoPorUsuarioId = usuarioId;
    }

    public void revogar(Long usuarioId, Instant instante) {
        exigirPendente();
        this.estado = EstadoConviteOrientacao.REVOGADO;
        this.tokenHash = null;
        this.revogadoEm = instante;
        this.revogadoPorUsuarioId = usuarioId;
    }

    public void expirar() {
        exigirPendente();
        this.estado = EstadoConviteOrientacao.EXPIRADO;
        this.tokenHash = null;
    }

    private void exigirPendente() {
        if (estado != EstadoConviteOrientacao.PENDENTE) {
            throw new IllegalStateException("O convite não está pendente.");
        }
    }

    @Override
    public String toString() {
        return "ConviteOrientacao[id=" + getId() + ", laboratorioId=" + laboratorio.getId()
                + ", estado=" + estado + "]";
    }
}
