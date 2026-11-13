package br.ufla.citogenetica.usuario.internal.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import br.ufla.citogenetica.usuario.internal.persistence.EmailChangeConfirmationRepository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;

@Service
public class EmailConfirmationCleanupService {

    private final JdbcTemplate jdbcTemplate;
    private final EmailConfirmationProperties properties;
    private final Clock clock;
    private final EmailChangeConfirmationRepository emailChangeRepository;

    public EmailConfirmationCleanupService(JdbcTemplate jdbcTemplate,
                                           EmailConfirmationProperties properties,
                                           Clock clock,
                                           EmailChangeConfirmationRepository emailChangeRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = clock;
        this.emailChangeRepository = emailChangeRepository;
    }

    @Scheduled(fixedDelayString = "${api.security.email-confirmation.cleanup-interval-ms}")
    public int removeExpiredPendingRegistrations() {
        var cutoff = clock.instant().minus(Duration.ofDays(properties.pendingRetentionDays()));
        int removedRegistrations = jdbcTemplate.update("""
                WITH cadastros_vencidos AS (
                    SELECT id
                    FROM usuario
                    WHERE email_confirmado_em IS NULL AND created_date < ?
                    ORDER BY created_date, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                DELETE FROM usuario usuario
                USING cadastros_vencidos vencido
                WHERE usuario.id = vencido.id
                """, Timestamp.from(cutoff), properties.cleanupBatchSize());
        emailChangeRepository.deleteExpired(clock.instant(), properties.cleanupBatchSize());
        return removedRegistrations;
    }
}
