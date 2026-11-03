package br.ufla.citogenetica.usuario.internal.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EmailChangeConfirmationRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmailChangeConfirmationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replace(long userId, UUID challengeId, String newEmail, byte[] tokenHash,
                        Instant createdAt, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO desafio_troca_email
                    (id, usuario_id, email_novo, token_hash, criado_em, expira_em)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (usuario_id) DO UPDATE SET
                    id = EXCLUDED.id,
                    email_novo = EXCLUDED.email_novo,
                    token_hash = EXCLUDED.token_hash,
                    criado_em = EXCLUDED.criado_em,
                    expira_em = EXCLUDED.expira_em,
                    invalidado_em = NULL,
                    consumido_em = NULL
                """, challengeId, userId, newEmail, tokenHash,
                Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    public void invalidatePending(long userId, Instant now) {
        jdbcTemplate.update("""
                UPDATE desafio_troca_email
                SET invalidado_em = ?
                WHERE usuario_id = ? AND invalidado_em IS NULL AND consumido_em IS NULL
                """, Timestamp.from(now), userId);
    }

    public Optional<Long> findUserIdByHash(byte[] tokenHash) {
        return jdbcTemplate.query("SELECT usuario_id FROM desafio_troca_email WHERE token_hash = ?",
                (resultSet, rowNumber) -> resultSet.getLong("usuario_id"), tokenHash).stream().findFirst();
    }

    public void lockNormalizedEmail(String normalizedEmail) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", resultSet -> null,
                normalizedEmail);
    }

    public Optional<EmailChangeChallenge> findByUserIdForUpdate(long userId) {
        return jdbcTemplate.query("""
                        SELECT email_novo, token_hash, expira_em, invalidado_em, consumido_em
                        FROM desafio_troca_email
                        WHERE usuario_id = ?
                        FOR UPDATE
                        """,
                resultSet -> resultSet.next()
                        ? Optional.of(new EmailChangeChallenge(
                                resultSet.getString("email_novo"),
                                resultSet.getBytes("token_hash"),
                                resultSet.getTimestamp("expira_em").toInstant(),
                                toInstant(resultSet.getTimestamp("invalidado_em")),
                                toInstant(resultSet.getTimestamp("consumido_em"))))
                        : Optional.empty(), userId);
    }

    public int consume(long userId, byte[] tokenHash, Instant now) {
        return jdbcTemplate.update("""
                UPDATE desafio_troca_email
                SET consumido_em = ?
                WHERE usuario_id = ? AND token_hash = ? AND expira_em > ?
                  AND invalidado_em IS NULL AND consumido_em IS NULL
                """, Timestamp.from(now), userId, tokenHash, Timestamp.from(now));
    }

    public int deleteExpired(Instant now, int batchSize) {
        return jdbcTemplate.update("""
                WITH expirados AS (
                    SELECT usuario_id
                    FROM desafio_troca_email
                    WHERE expira_em <= ?
                    ORDER BY expira_em, usuario_id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                DELETE FROM desafio_troca_email desafio
                USING expirados
                WHERE desafio.usuario_id = expirados.usuario_id
                """, Timestamp.from(now), batchSize);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record EmailChangeChallenge(String newEmail, byte[] tokenHash, Instant expiresAt,
                                       Instant invalidatedAt, Instant consumedAt) {
        public boolean isUsableAt(Instant now) {
            return invalidatedAt == null && consumedAt == null && expiresAt.isAfter(now);
        }
    }
}
