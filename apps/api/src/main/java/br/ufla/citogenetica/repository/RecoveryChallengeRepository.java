package br.ufla.citogenetica.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RecoveryChallengeRepository {

    private final JdbcTemplate jdbcTemplate;

    public RecoveryChallengeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replace(long userId, UUID challengeId, byte[] codeHmac, Instant createdAt, Instant expiresAt) {
        jdbcTemplate.update("""
                        INSERT INTO desafio_recuperacao_senha
                            (usuario_id, id, codigo_hmac, criado_em, expira_em, tentativas, invalidado_em, consumido_em)
                        VALUES (?, ?, ?, ?, ?, 0, NULL, NULL)
                        ON CONFLICT (usuario_id) DO UPDATE SET
                            id = EXCLUDED.id,
                            codigo_hmac = EXCLUDED.codigo_hmac,
                            criado_em = EXCLUDED.criado_em,
                            expira_em = EXCLUDED.expira_em,
                            tentativas = 0,
                            invalidado_em = NULL,
                            consumido_em = NULL
                        """,
                userId, challengeId, codeHmac, Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    public Optional<RecoveryChallenge> findByUserIdForUpdate(long userId) {
        return jdbcTemplate.query("""
                        SELECT id, codigo_hmac, expira_em, tentativas, invalidado_em, consumido_em
                        FROM desafio_recuperacao_senha
                        WHERE usuario_id = ?
                        FOR UPDATE
                        """,
                resultSet -> resultSet.next()
                        ? Optional.of(new RecoveryChallenge(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getBytes("codigo_hmac"),
                                resultSet.getTimestamp("expira_em").toInstant(),
                                resultSet.getInt("tentativas"),
                                toInstant(resultSet.getTimestamp("invalidado_em")),
                                toInstant(resultSet.getTimestamp("consumido_em"))))
                        : Optional.empty(),
                userId);
    }

    public void registerInvalidAttempt(long userId, UUID challengeId, Instant now, int maxAttempts) {
        jdbcTemplate.update("""
                        UPDATE desafio_recuperacao_senha
                        SET tentativas = tentativas + 1,
                            invalidado_em = CASE WHEN tentativas + 1 >= ? THEN ? ELSE invalidado_em END
                        WHERE usuario_id = ? AND id = ?
                        """,
                maxAttempts, Timestamp.from(now), userId, challengeId);
    }

    public void consume(long userId, UUID challengeId, Instant now) {
        jdbcTemplate.update("""
                        UPDATE desafio_recuperacao_senha
                        SET consumido_em = ?
                        WHERE usuario_id = ? AND id = ? AND consumido_em IS NULL AND invalidado_em IS NULL
                        """,
                Timestamp.from(now), userId, challengeId);
    }

    public void invalidate(long userId, Instant now) {
        jdbcTemplate.update("""
                        UPDATE desafio_recuperacao_senha
                        SET invalidado_em = ?
                        WHERE usuario_id = ? AND invalidado_em IS NULL AND consumido_em IS NULL
                        """,
                Timestamp.from(now), userId);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record RecoveryChallenge(UUID id, byte[] codeHmac, Instant expiresAt, int attempts,
                                    Instant invalidatedAt, Instant consumedAt) {
        public boolean isUsableAt(Instant now) {
            return invalidatedAt == null && consumedAt == null && expiresAt.isAfter(now);
        }
    }
}
