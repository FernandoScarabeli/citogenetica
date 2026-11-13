package br.ufla.citogenetica.contagem.internal.domain;

import java.util.UUID;

import br.ufla.citogenetica.shared.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "protocolo_fase")
@Getter
@NoArgsConstructor
public class FaseProtocolo extends UuidEntity {

    @Column(name = "versao_id", nullable = false, updatable = false)
    private UUID versaoId;

    @Column(name = "nome", nullable = false, length = 120, updatable = false)
    private String nome;

    @Column(name = "ordem", nullable = false, updatable = false)
    private int ordem;

    @Column(name = "conta_como_divisao", nullable = false, updatable = false)
    private boolean contaComoDivisao;
}
