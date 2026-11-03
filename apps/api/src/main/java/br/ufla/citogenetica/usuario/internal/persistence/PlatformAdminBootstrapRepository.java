package br.ufla.citogenetica.usuario.internal.persistence;

import br.ufla.citogenetica.usuario.api.error.PlatformAdminBootstrapException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class PlatformAdminBootstrapRepository {

    private final JdbcTemplate jdbcTemplate;

    public PlatformAdminBootstrapRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean lockAndIsConsumed() {
        return jdbcTemplate.query("""
                        SELECT consumido_em IS NOT NULL AS consumido
                        FROM bootstrap_admin_plataforma
                        WHERE id = 1
                        FOR UPDATE
                        """,
                (resultSet, rowNumber) -> resultSet.getBoolean("consumido"))
                .stream()
                .findFirst()
                .orElseThrow(() -> new PlatformAdminBootstrapException(
                        "O marcador de uso único do primeiro administrador não foi inicializado."));
    }

    public void consume(Instant consumedAt, long userId) {
        int rowsUpdated = jdbcTemplate.update("""
                UPDATE bootstrap_admin_plataforma
                SET consumido_em = ?, usuario_id = ?
                WHERE id = 1 AND consumido_em IS NULL AND usuario_id IS NULL
                """, Timestamp.from(consumedAt), userId);
        if (rowsUpdated != 1) {
            throw new PlatformAdminBootstrapException(
                    "O bootstrap do primeiro administrador já foi consumido por outra execução.");
        }
    }
}
