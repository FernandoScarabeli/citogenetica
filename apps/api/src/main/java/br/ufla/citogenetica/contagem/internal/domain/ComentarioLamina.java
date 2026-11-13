package br.ufla.citogenetica.contagem.internal.domain;

import java.util.UUID;

import br.ufla.citogenetica.shared.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Comentário deixado numa lâmina (por exemplo, o orientador para o aluno). */
@Entity
@Table(name = "comentario_lamina")
@Getter
@NoArgsConstructor
public class ComentarioLamina extends UuidEntity {

    @Column(name = "lamina_id", nullable = false, updatable = false)
    private UUID laminaId;

    @Column(name = "autor_usuario_id", nullable = false, updatable = false)
    private Long autorUsuarioId;

    @Column(name = "texto", nullable = false, updatable = false, length = 2000)
    private String texto;

    public ComentarioLamina(UUID id, UUID laminaId, Long autorUsuarioId, String texto) {
        super(id);
        this.laminaId = laminaId;
        this.autorUsuarioId = autorUsuarioId;
        this.texto = texto;
    }
}
