package br.ufla.citogenetica.contagem.internal.domain;

import java.time.Instant;
import java.util.UUID;

import br.ufla.citogenetica.shared.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Lâmina de contagem. Dono, laboratório (nulo = avulsa) e versão do protocolo nunca mudam depois da criação,
 * que é feita pelo repositório com {@code INSERT ... ON CONFLICT DO NOTHING}. Só os campos descritivos e a
 * exclusão lógica são alterados, sempre sob controle de versão otimista.
 */
@Entity
@Table(name = "lamina")
@Getter
@NoArgsConstructor
public class Lamina extends UuidEntity {

    @Column(name = "dono_usuario_id", nullable = false, updatable = false)
    private Long donoUsuarioId;

    @Column(name = "laboratorio_id", updatable = false)
    private UUID laboratorioId;

    @Column(name = "protocolo_versao_id", nullable = false, updatable = false)
    private UUID protocoloVersaoId;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "especie", nullable = false, length = 120)
    private String especie;

    @Column(name = "tratamento", nullable = false, length = 120)
    private String tratamento;

    @Column(name = "observacoes", nullable = false, length = 5000)
    private String observacoes;

    @Column(name = "meta")
    private Integer meta;

    @Column(name = "excluida_em")
    private Instant excluidaEm;

    public boolean estaExcluida() {
        return excluidaEm != null;
    }

    public boolean ehAvulsa() {
        return laboratorioId == null;
    }

    public void substituirDescricao(String nome, String especie, String tratamento, String observacoes, Integer meta) {
        this.nome = nome;
        this.especie = especie;
        this.tratamento = tratamento;
        this.observacoes = observacoes;
        this.meta = meta;
    }

    public void excluir(Instant quando) {
        this.excluidaEm = quando;
    }
}
