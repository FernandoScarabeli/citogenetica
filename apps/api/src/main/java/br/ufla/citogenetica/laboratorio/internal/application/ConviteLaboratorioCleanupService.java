package br.ufla.citogenetica.laboratorio.internal.application;

import java.sql.Timestamp;
import java.time.Clock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConviteLaboratorioCleanupService {

    private final JdbcTemplate jdbcTemplate;
    private final ConviteLaboratorioProperties properties;
    private final Clock clock;

    public ConviteLaboratorioCleanupService(JdbcTemplate jdbcTemplate,
                                            ConviteLaboratorioProperties properties,
                                            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${api.security.laboratory-invitations.cleanup-interval-ms}")
    @Transactional
    public int expirarVencidos() {
        Timestamp agora = Timestamp.from(clock.instant());
        return jdbcTemplate.update("""
                WITH vencidos AS (
                    SELECT id
                      FROM convite_laboratorio
                     WHERE estado = 'PENDENTE' AND expires_at <= ?
                     ORDER BY expires_at, id
                     LIMIT ?
                     FOR UPDATE SKIP LOCKED
                )
                UPDATE convite_laboratorio convite
                   SET estado = 'EXPIRADO', token_hash = NULL,
                       version = version + 1, last_modified_at = ?
                  FROM vencidos
                 WHERE convite.id = vencidos.id
                """, agora, properties.cleanupBatchSize(), agora);
    }
}
