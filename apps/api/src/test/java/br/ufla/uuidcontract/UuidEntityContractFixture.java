package br.ufla.uuidcontract;

import br.ufla.citogenetica.shared.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Entidade exclusiva do teste de contrato da base UUID. */
@Entity
@Table(name = "uuid_entity_contract_test")
@Getter
@Setter
@NoArgsConstructor
class UuidEntityContractFixture extends UuidEntity {

    @Column(name = "conteudo", nullable = false)
    private String conteudo;

    UuidEntityContractFixture(java.util.UUID id, String conteudo) {
        super(id);
        this.conteudo = conteudo;
    }
}
