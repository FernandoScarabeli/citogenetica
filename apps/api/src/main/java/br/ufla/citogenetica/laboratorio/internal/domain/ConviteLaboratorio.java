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
@Table(name = "convite_laboratorio")
@Getter
@NoArgsConstructor
public class ConviteLaboratorio extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratorio_id", nullable = false, updatable = false)
    private Laboratorio laboratorio;

    @Column(name = "convidado_por_usuario_id", nullable = false, updatable = false)
    private Long convidadoPorUsuarioId;

    @Column(name = "email_destino", nullable = false, length = 254, updatable = false)
    private String emailDestino;

    @Column(name = "token_hash", columnDefinition = "bytea", length = 32)
    private byte[] tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoConviteLaboratorio estado;

    @Column(name = "conceder_admin", nullable = false)
    private boolean concederAdmin;

    @Column(name = "conceder_orientador", nullable = false)
    private boolean concederOrientador;

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

    @Column(name = "participacao_id", columnDefinition = "uuid")
    private UUID participacaoId;

    public ConviteLaboratorio(UUID id, Laboratorio laboratorio, Long convidadoPorUsuarioId,
                              String emailDestino, byte[] tokenHash, boolean concederAdmin,
                              boolean concederOrientador, Instant expiraEm) {
        super(id);
        this.laboratorio = laboratorio;
        this.convidadoPorUsuarioId = convidadoPorUsuarioId;
        this.emailDestino = emailDestino;
        this.tokenHash = tokenHash.clone();
        this.estado = EstadoConviteLaboratorio.PENDENTE;
        this.concederAdmin = concederAdmin;
        this.concederOrientador = concederOrientador;
        this.expiraEm = expiraEm;
    }

    public void reenviar(byte[] novoTokenHash, Instant novaExpiracao) {
        if (estado != EstadoConviteLaboratorio.PENDENTE && estado != EstadoConviteLaboratorio.EXPIRADO) {
            throw new IllegalStateException("Somente convite pendente ou expirado pode ser reenviado.");
        }
        this.estado = EstadoConviteLaboratorio.PENDENTE;
        this.tokenHash = novoTokenHash.clone();
        this.expiraEm = novaExpiracao;
        this.respondidoEm = null;
        this.respondidoPorUsuarioId = null;
        this.revogadoEm = null;
        this.revogadoPorUsuarioId = null;
        this.participacaoId = null;
    }

    public void aceitar(Long usuarioId, UUID novaParticipacaoId, Instant instante) {
        exigirPendente();
        this.estado = EstadoConviteLaboratorio.ACEITO;
        this.tokenHash = null;
        this.respondidoEm = instante;
        this.respondidoPorUsuarioId = usuarioId;
        this.participacaoId = novaParticipacaoId;
    }

    public void recusar(Long usuarioId, Instant instante) {
        exigirPendente();
        this.estado = EstadoConviteLaboratorio.RECUSADO;
        this.tokenHash = null;
        this.respondidoEm = instante;
        this.respondidoPorUsuarioId = usuarioId;
    }

    public void revogar(Long usuarioId, Instant instante) {
        exigirPendente();
        this.estado = EstadoConviteLaboratorio.REVOGADO;
        this.tokenHash = null;
        this.revogadoEm = instante;
        this.revogadoPorUsuarioId = usuarioId;
    }

    public void expirar() {
        exigirPendente();
        this.estado = EstadoConviteLaboratorio.EXPIRADO;
        this.tokenHash = null;
    }

    private void exigirPendente() {
        if (estado != EstadoConviteLaboratorio.PENDENTE) {
            throw new IllegalStateException("O convite não está pendente.");
        }
    }

    @Override
    public String toString() {
        return "ConviteLaboratorio[id=" + getId() + ", laboratorioId=" + laboratorio.getId()
                + ", estado=" + estado + "]";
    }
}
