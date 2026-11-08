package br.ufla.citogenetica.contagem.internal.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registro de contagem: um lote de {@code quantidade} células da mesma fase e situação. A criação é feita pelo
 * repositório com {@code INSERT ... ON CONFLICT DO NOTHING}. {@code registradoEm} é a hora oficial do servidor;
 * hora e fuso do aparelho são só informativos, e o hash da criação identifica o pedido original; nenhum desses
 * muda. Fase, situação e quantidade mudam por edição ou por desfazer, e a exclusão é lógica. Os tipos de
 * anormalidade ficam em {@code registro_contagem_tipo}.
 */
@Entity
@Table(name = "registro_contagem")
@Getter
@NoArgsConstructor
public class RegistroContagem extends UuidEntity {

    @Column(name = "lamina_id", nullable = false, updatable = false)
    private UUID laminaId;

    @Column(name = "fase_id", nullable = false)
    private UUID faseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private StatusRegistro status;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    @Column(name = "registrado_em", nullable = false, updatable = false)
    private Instant registradoEm;

    @Column(name = "hora_aparelho", updatable = false)
    private Instant horaAparelho;

    @Column(name = "fuso_aparelho", length = 64, updatable = false)
    private String fusoAparelho;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hash_conteudo_criacao", nullable = false, length = 64, updatable = false)
    private String hashConteudoCriacao;

    @Column(name = "excluido_em")
    private Instant excluidoEm;

    public boolean estaExcluido() {
        return excluidoEm != null;
    }

    public boolean mudaCampos(UUID novaFaseId, StatusRegistro novoStatus, int novaQuantidade) {
        return !faseId.equals(novaFaseId) || status != novoStatus || quantidade != novaQuantidade;
    }

    public void alterar(UUID novaFaseId, StatusRegistro novoStatus, int novaQuantidade) {
        this.faseId = novaFaseId;
        this.status = novoStatus;
        this.quantidade = novaQuantidade;
    }

    /** Desfazer só parte do lote: o registro continua ativo com menos células. */
    public void reduzirQuantidade(int celulas) {
        this.quantidade -= celulas;
    }

    public void excluir(Instant quando) {
        this.excluidoEm = quando;
    }
}
