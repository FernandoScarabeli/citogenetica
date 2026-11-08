package br.ufla.citogenetica.contagem.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Parâmetros da contagem: linha única (id 1), fora da sincronização entre aparelhos. */
@Entity
@Table(name = "parametro_contagem")
@Getter
@NoArgsConstructor
public class ParametroContagem {

    public static final short ID_UNICO = 1;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Short id;

    @Column(name = "limite_celulas_por_registro", nullable = false)
    private int limiteCelulasPorRegistro;
}
