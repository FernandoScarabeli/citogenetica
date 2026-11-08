package br.ufla.citogenetica.contagem.internal.domain;

import java.time.Instant;
import java.util.UUID;

import br.ufla.citogenetica.shared.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Versão de um protocolo. Depois de publicada, ela e suas fases e tipos fixos são imutáveis (triggers do banco). */
@Entity
@Table(name = "protocolo_versao")
@Getter
@NoArgsConstructor
public class VersaoProtocolo extends UuidEntity {

    @Column(name = "protocolo_id", nullable = false, updatable = false)
    private UUID protocoloId;

    @Column(name = "numero", nullable = false, updatable = false)
    private int numero;

    @Column(name = "publicada_em", updatable = false)
    private Instant publicadaEm;
}
