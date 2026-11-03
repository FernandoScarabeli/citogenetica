package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.usuario.api.error.EmailConfirmationException;
import br.ufla.citogenetica.usuario.api.error.ValidationConflictException;
import br.ufla.citogenetica.usuario.internal.domain.EmailNormalizer;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.domain.event.EmailAddressChangedEvent;
import br.ufla.citogenetica.usuario.internal.domain.event.EmailChangeConfirmationRequestedEvent;
import br.ufla.citogenetica.usuario.internal.persistence.EmailChangeConfirmationRepository;
import br.ufla.citogenetica.usuario.internal.persistence.RecoveryChallengeRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import br.ufla.citogenetica.usuario.internal.web.dto.request.SolicitarTrocaEmailRequestDTO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailChangeService {

    private static final int TOKEN_BYTES = 32;
    private static final String TOKEN_FORMAT = "[A-Za-z0-9_-]{43}";

    private final UsuarioRepository usuarioRepository;
    private final EmailChangeConfirmationRepository emailChangeRepository;
    private final RecoveryChallengeRepository recoveryChallengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimitService;
    private final EmailChangeConfirmationProperties properties;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionRevocationService sessionRevocationService;
    private final TransactionTemplate transactionTemplate;

    public EmailChangeService(UsuarioRepository usuarioRepository,
                              EmailChangeConfirmationRepository emailChangeRepository,
                              RecoveryChallengeRepository recoveryChallengeRepository,
                              PasswordEncoder passwordEncoder,
                              RateLimitService rateLimitService,
                              EmailChangeConfirmationProperties properties,
                              SecureRandom secureRandom,
                              Clock clock,
                              ApplicationEventPublisher eventPublisher,
                              SessionRevocationService sessionRevocationService,
                              PlatformTransactionManager transactionManager) {
        this.usuarioRepository = usuarioRepository;
        this.emailChangeRepository = emailChangeRepository;
        this.recoveryChallengeRepository = recoveryChallengeRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.secureRandom = secureRandom;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
        this.sessionRevocationService = sessionRevocationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public void solicitarTrocaEmail(long userId, SolicitarTrocaEmailRequestDTO request) {
        rateLimitService.enforceUser(AuthRateLimitRoute.CHANGE_EMAIL, userId);
        Usuario usuario = usuarioRepository.findByIdForUpdate(userId)
                .orElseThrow(EmailConfirmationException::new);
        if (usuario.getStatus() != StatusEnum.ATIVO || usuario.getEmailConfirmadoEm() == null) {
            throw new EmailConfirmationException();
        }
        // Mesmo contrato da troca de senha: o usuário está logado, então senha errada não é falha de sessão.
        boolean senhaDentroDoLimiteBcrypt = request.senhaAtual().getBytes(StandardCharsets.UTF_8).length <= 72;
        if (!senhaDentroDoLimiteBcrypt || !passwordEncoder.matches(request.senhaAtual(), usuario.getPassword())) {
            throw new ValidationConflictException("A senha atual está incorreta.");
        }

        String newEmail = EmailNormalizer.normalize(request.novoEmail());
        Instant now = clock.instant();
        emailChangeRepository.invalidatePending(userId, now);

        if (newEmail.equals(usuario.getEmail())
                || usuarioRepository.existsByEmailIgnoreCaseAndIdNot(newEmail, userId)) {
            return;
        }

        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        byte[] tokenHash = sha256(token.getBytes(StandardCharsets.UTF_8));
        Instant expiresAt = now.plus(Duration.ofHours(properties.expirationHours()));
        emailChangeRepository.replace(userId, UUID.randomUUID(), newEmail, tokenHash, now, expiresAt);

        String confirmationUrl = properties.frontendUrl() + "#token=" + token;
        eventPublisher.publishEvent(new EmailChangeConfirmationRequestedEvent(
                newEmail, confirmationUrl, properties.expirationHours()));
    }

    public void confirmarTrocaEmail(String token) {
        try {
            transactionTemplate.executeWithoutResult(status -> confirmarDentroDaTransacao(token));
        } catch (RuntimeException exception) {
            if (isUniqueViolation(exception)) {
                throw new EmailConfirmationException();
            }
            throw exception;
        }
    }

    private void confirmarDentroDaTransacao(String token) {
        if (token == null || !token.matches(TOKEN_FORMAT)) {
            throw new EmailConfirmationException();
        }

        byte[] tokenHash = sha256(token.getBytes(StandardCharsets.UTF_8));
        Optional<Long> userId = emailChangeRepository.findUserIdByHash(tokenHash);
        if (userId.isEmpty()) {
            throw new EmailConfirmationException();
        }

        Usuario usuario = usuarioRepository.findByIdForUpdate(userId.get())
                .orElseThrow(EmailConfirmationException::new);
        rateLimitService.enforceUser(AuthRateLimitRoute.CONFIRM_EMAIL_CHANGE, userId.get());
        EmailChangeConfirmationRepository.EmailChangeChallenge challenge =
                emailChangeRepository.findByUserIdForUpdate(userId.get())
                        .orElseThrow(EmailConfirmationException::new);
        Instant now = clock.instant();

        if (!usuario.isEnabled()
                || !challenge.isUsableAt(now)
                || !MessageDigest.isEqual(tokenHash, challenge.tokenHash())
                || EmailNormalizer.normalize(challenge.newEmail()).equals(usuario.getEmail())) {
            throw new EmailConfirmationException();
        }

        emailChangeRepository.lockNormalizedEmail(challenge.newEmail());
        if (usuarioRepository.existsByEmailIgnoreCaseAndIdNot(challenge.newEmail(), usuario.getId())) {
            throw new EmailConfirmationException();
        }

        if (emailChangeRepository.consume(userId.get(), tokenHash, now) != 1) {
            throw new EmailConfirmationException();
        }

        String previousEmail = usuario.getEmail();
        usuario.setEmail(challenge.newEmail());
        usuario.setEmailConfirmadoEm(now);
        usuarioRepository.saveAndFlush(usuario);

        recoveryChallengeRepository.invalidate(usuario.getId(), now);
        sessionRevocationService.revogarSessoes(usuario.getId(), null);
        eventPublisher.publishEvent(new EmailAddressChangedEvent(previousEmail));
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível nesta JVM.", exception);
        }
    }

    private boolean isUniqueViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
