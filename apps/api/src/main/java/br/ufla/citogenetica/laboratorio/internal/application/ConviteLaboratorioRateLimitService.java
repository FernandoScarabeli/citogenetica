package br.ufla.citogenetica.laboratorio.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import br.ufla.citogenetica.usuario.api.error.TooManyRequestsException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConviteLaboratorioRateLimitService {

    private final JdbcTemplate jdbcTemplate;
    private final ConviteLaboratorioProperties properties;
    private final Clock clock;

    public ConviteLaboratorioRateLimitService(JdbcTemplate jdbcTemplate,
                                              ConviteLaboratorioProperties properties,
                                              Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void limitarEnvio(UUID laboratorioId, long autorId) {
        limitar("ENVIO", "LABORATORIO_AUTOR",
                laboratorioId + ":" + autorId,
                properties.rateLimit().sendPerLaboratoryAuthor());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void limitarAceitePorIp(String enderecoIp) {
        String chave = enderecoIp == null || enderecoIp.isBlank() ? "endereco-indisponivel" : enderecoIp;
        limitar("CONSUMO", "IP", chave, properties.rateLimit().acceptPerIp());
    }

    /** Prévia pública do convite: contador próprio, para não gastar as tentativas de aceite. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void limitarPreviaPorIp(String enderecoIp) {
        String chave = enderecoIp == null || enderecoIp.isBlank() ? "endereco-indisponivel" : enderecoIp;
        limitar("PREVIA", "IP", chave, properties.rateLimit().acceptPerIp());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void limitarAceitePorUsuario(long usuarioId) {
        limitar("CONSUMO", "USUARIO", Long.toString(usuarioId), properties.rateLimit().acceptPerUser());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Scheduled(fixedDelayString = "${api.security.laboratory-invitations.cleanup-interval-ms}")
    public int limparJanelasExpiradas() {
        Instant limite = clock.instant().minus(Duration.ofMinutes(properties.rateLimit().windowMinutes()));
        return jdbcTemplate.update("DELETE FROM convite_laboratorio_rate_limit WHERE janela_inicio < ?",
                java.sql.Timestamp.from(limite));
    }

    private void limitar(String operacao, String tipoChave, String chave, int limite) {
        Instant agora = clock.instant();
        long segundosJanela = Duration.ofMinutes(properties.rateLimit().windowMinutes()).toSeconds();
        long epochJanela = Math.floorDiv(agora.getEpochSecond(), segundosJanela) * segundosJanela;
        Instant inicioJanela = Instant.ofEpochSecond(epochJanela);
        try {
            jdbcTemplate.queryForObject("""
                            INSERT INTO convite_laboratorio_rate_limit
                                (operacao, tipo_chave, chave_hash, janela_inicio, tentativas)
                            VALUES (?, ?, ?, ?, 1)
                            ON CONFLICT (operacao, tipo_chave, chave_hash, janela_inicio)
                            DO UPDATE SET tentativas = convite_laboratorio_rate_limit.tentativas + 1
                            WHERE convite_laboratorio_rate_limit.tentativas < ?
                            RETURNING tentativas
                            """,
                    Integer.class, operacao, tipoChave, sha256(chave),
                    java.sql.Timestamp.from(inicioJanela), limite);
        } catch (EmptyResultDataAccessException exception) {
            Duration espera = Duration.between(agora, inicioJanela.plusSeconds(segundosJanela));
            long retryAfter = Math.max(1, espera.getSeconds() + (espera.getNano() > 0 ? 1 : 0));
            throw new TooManyRequestsException(retryAfter);
        }
    }

    private static byte[] sha256(String chave) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(chave.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível.", exception);
        }
    }
}
