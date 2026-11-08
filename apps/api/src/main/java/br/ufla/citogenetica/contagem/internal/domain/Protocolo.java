package br.ufla.citogenetica.contagem.internal.domain;

import br.ufla.citogenetica.shared.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Protocolo de contagem (meiose, mitose). Criado somente por migration; a API apenas lê. */
@Entity
@Table(name = "protocolo")
@Getter
@NoArgsConstructor
public class Protocolo extends UuidEntity {

    @Column(name = "codigo", nullable = false, length = 40, updatable = false)
    private String codigo;

    @Column(name = "nome", nullable = false, length = 120, updatable = false)
    private String nome;
}
