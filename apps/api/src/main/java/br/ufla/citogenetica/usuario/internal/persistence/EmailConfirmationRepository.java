package br.ufla.citogenetica.usuario.internal.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EmailConfirmationRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmailConfirmationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replace(long userId, UUID challengeId, byte[] tokenHash, Instant createdAt, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO desafio_confirmacao_email (id, usuario_id, token_hash, criado_em, expira_em)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (usuario_id) DO UPDATE
                SET id = EXCLUDED.id,
                    token_hash = EXCLUDED.token_hash,
                    criado_em = EXCLUDED.criado_em,
                    expira_em = EXCLUDED.expira_em,
                    invalidado_em = NULL,
                    consumido_em = NULL
                """, challengeId, userId, tokenHash, Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    public Optional<Long> findUserIdByHash(byte[] tokenHash) {
        return jdbcTemplate.query("SELECT usuario_id FROM desafio_confirmacao_email WHERE token_hash = ?",
                (resultSet, rowNumber) -> resultSet.getLong("usuario_id"), tokenHash).stream().findFirst();
    }

    public int consume(long userId, byte[] tokenHash, Instant now) {
        return jdbcTemplate.update("""
                UPDATE desafio_confirmacao_email
                SET consumido_em = ?
                WHERE usuario_id = ? AND token_hash = ? AND expira_em > ?
                  AND invalidado_em IS NULL AND consumido_em IS NULL
                """, Timestamp.from(now), userId, tokenHash, Timestamp.from(now));
    }
}
