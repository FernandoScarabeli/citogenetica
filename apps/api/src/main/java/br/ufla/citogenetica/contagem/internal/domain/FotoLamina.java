package br.ufla.citogenetica.contagem.internal.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.shared.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Foto de uma lâmina. O arquivo fica no armazenamento; aqui só a chave e os dados para listar. */
@Entity
@Table(name = "foto_lamina")
@Getter
@NoArgsConstructor
public class FotoLamina extends UuidEntity {

    @Column(name = "lamina_id", nullable = false, updatable = false)
    private UUID laminaId;

    @Column(name = "chave", nullable = false, updatable = false, length = 80)
    private String chave;

    @Column(name = "tamanho_bytes", nullable = false, updatable = false)
    private int tamanhoBytes;

    @Column(name = "legenda", length = 200)
    private String legenda;

    @Column(name = "enviada_por_usuario_id", nullable = false, updatable = false)
    private Long enviadaPorUsuarioId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "anotacoes", nullable = false, columnDefinition = "jsonb")
    private List<AnotacaoFoto> anotacoes = new ArrayList<>();

    public FotoLamina(UUID id, UUID laminaId, String chave, int tamanhoBytes, String legenda, Long enviadaPorUsuarioId) {
        super(id);
        this.laminaId = laminaId;
        this.chave = chave;
        this.tamanhoBytes = tamanhoBytes;
        this.legenda = legenda;
        this.enviadaPorUsuarioId = enviadaPorUsuarioId;
    }

    /** Troca a legenda e as marcações já validadas; a imagem original nunca muda. */
    public void editar(String legenda, List<AnotacaoFoto> anotacoes) {
        this.legenda = legenda;
        this.anotacoes = new ArrayList<>(anotacoes);
    }
}
