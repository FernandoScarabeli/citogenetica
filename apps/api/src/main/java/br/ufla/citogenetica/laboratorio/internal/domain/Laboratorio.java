package br.ufla.citogenetica.laboratorio.internal.domain;

import java.time.Instant;
import java.util.UUID;

import br.ufla.citogenetica.shared.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "laboratorio")
@Getter
@NoArgsConstructor
public class Laboratorio extends UuidEntity {

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoLaboratorio estado;

    @Column(name = "criado_por_usuario_id", nullable = false)
    private Long criadoPorUsuarioId;

    @Column(name = "desativado_em")
    private Instant desativadoEm;

    @Column(name = "desativado_por_usuario_id")
    private Long desativadoPorUsuarioId;

    public Laboratorio(UUID id, String nome, Long criadoPorUsuarioId) {
        super(id);
        this.nome = nome;
        this.criadoPorUsuarioId = criadoPorUsuarioId;
        this.estado = EstadoLaboratorio.ATIVO;
    }

    public void atualizarNome(String nome) {
        this.nome = nome;
    }

    public void desativar(Long operadorId, Instant instante) {
        this.estado = EstadoLaboratorio.DESATIVADO;
        this.desativadoEm = instante;
        this.desativadoPorUsuarioId = operadorId;
    }

    public void reativar() {
        this.estado = EstadoLaboratorio.ATIVO;
        this.desativadoEm = null;
        this.desativadoPorUsuarioId = null;
    }
}
