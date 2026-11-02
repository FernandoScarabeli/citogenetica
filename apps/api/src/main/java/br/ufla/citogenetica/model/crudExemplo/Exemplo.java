package br.ufla.citogenetica.model.crudExemplo;

import br.ufla.citogenetica.model.baseEntity.BaseEntity;
import br.ufla.citogenetica.model.type.ExemploEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

// Anotações do Lombok para gerar código boilerplate.
// É preferível usar anotações específicas em vez de @Data em entidades JPA.
@Getter
@Setter
@SuperBuilder // Permite usar o padrão Builder com herança da BaseEntity
@NoArgsConstructor
@AllArgsConstructor

// Anotações do JPA
@Entity
@Table(name = "exemplo")
public class Exemplo extends BaseEntity<Long> {

    @Enumerated(EnumType.STRING) // Salva o nome do enum ("TIPO_A") no banco em vez do índice (0, 1, 2)
    @Column(name = "enum_type", nullable = false)
    private ExemploEnum enumType;

    @Column(name = "descricao", length = 255, nullable = false)
    private String descricao;

    // Define a precisão (total de dígitos) e a escala (dígitos após a vírgula)
    @Column(name = "valor", precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_exemplo", nullable = false)
    private LocalDate dataExemplo;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

//    Exemplo de relacionamento Muitos-para-Um
//    @ManyToOne(fetch = FetchType.LAZY) // LAZY é a melhor prática para performance
//    @JoinColumn(name = "responsavel_id", referencedColumnName = "id", nullable = false)
//    private usuario responsavel;
}
