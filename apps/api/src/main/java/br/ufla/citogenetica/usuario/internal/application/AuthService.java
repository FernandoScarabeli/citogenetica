package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.usuario.api.RolesEnum;
import br.ufla.citogenetica.usuario.api.error.EmailConfirmationException;
import br.ufla.citogenetica.usuario.api.error.ValidationConflictException;
import br.ufla.citogenetica.usuario.internal.domain.EmailNormalizer;
import br.ufla.citogenetica.usuario.internal.domain.Role;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.domain.event.EmailConfirmationRequestedEvent;
import br.ufla.citogenetica.usuario.internal.domain.event.RecoveryEmailRequestedEvent;
import br.ufla.citogenetica.usuario.internal.persistence.EmailConfirmationRepository;
import br.ufla.citogenetica.usuario.internal.persistence.RecoveryChallengeRepository;
import br.ufla.citogenetica.usuario.internal.persistence.RoleRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRegistrationRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import br.ufla.citogenetica.usuario.internal.web.dto.request.EmailConfirmationRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.LoginRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.RedefinirSenhaRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.ResendEmailConfirmationRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.UsuarioRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.ValidarCodigoRequestDTO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final UUID DUMMY_CHALLENGE_ID = new UUID(0, 0);
    private static final byte[] DUMMY_HMAC = new byte[32];
    private static final int CONFIRMATION_TOKEN_BYTES = 32;

    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final RecoveryChallengeRepository recoveryChallengeRepository;
    private final EmailConfirmationRepository emailConfirmationRepository;
    private final UsuarioRegistrationRepository usuarioRegistrationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final RecoveryHmacService recoveryHmacService;
    private final RecoveryProperties recoveryProperties;
    private final RecoveryExpirationProperties recoveryExpirationProperties;
    private final EmailConfirmationProperties emailConfirmationProperties;
    private final RateLimitService rateLimitService;
    private final SecureRandom secureRandom;
    private final Clock applicationClock;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionRevocationService sessionRevocationService;
    private final PasswordPolicy passwordPolicy;

    public AuthService(UsuarioRepository usuarioRepository,
                       RoleRepository roleRepository,
                       RecoveryChallengeRepository recoveryChallengeRepository,
                       EmailConfirmationRepository emailConfirmationRepository,
                       UsuarioRegistrationRepository usuarioRegistrationRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       RecoveryHmacService recoveryHmacService,
                       RecoveryProperties recoveryProperties,
                       RecoveryExpirationProperties recoveryExpirationProperties,
                       EmailConfirmationProperties emailConfirmationProperties,
                       RateLimitService rateLimitService,
                       SecureRandom secureRandom,
                       Clock applicationClock,
                       ApplicationEventPublisher eventPublisher,
                       SessionRevocationService sessionRevocationService,
                       PasswordPolicy passwordPolicy) {
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
        this.recoveryChallengeRepository = recoveryChallengeRepository;
        this.emailConfirmationRepository = emailConfirmationRepository;
        this.usuarioRegistrationRepository = usuarioRegistrationRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.recoveryHmacService = recoveryHmacService;
        this.recoveryProperties = recoveryProperties;
        this.recoveryExpirationProperties = recoveryExpirationProperties;
        this.emailConfirmationProperties = emailConfirmationProperties;
        this.rateLimitService = rateLimitService;
        this.secureRandom = secureRandom;
        this.applicationClock = applicationClock;
        this.eventPublisher = eventPublisher;
        this.sessionRevocationService = sessionRevocationService;
        this.passwordPolicy = passwordPolicy;
    }

    public Authentication login(LoginRequestDTO requestDTO) {
        String email = EmailNormalizer.normalize(requestDTO.email());
        rateLimitService.enforceEmail(AuthRateLimitRoute.LOGIN, email);
        if (requestDTO.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Credenciais inválidas.");
        }
        var usernamePassword = new UsernamePasswordAuthenticationToken(email, requestDTO.senha());
        return authenticationManager.authenticate(usernamePassword);
    }

    @Transactional
    public void register(UsuarioRequestDTO requestDTO) {
        String email = EmailNormalizer.normalize(requestDTO.email());
        rateLimitService.enforceEmail(AuthRateLimitRoute.REGISTER, email);
        passwordPolicy.validate(requestDTO.senha());
        // O hash é calculado sempre, para que o tempo de resposta não revele se o e-mail já existe.
        String senhaCodificada = passwordEncoder.encode(requestDTO.senha());

        Optional<Usuario> existente = usuarioRepository.findByEmailForUpdate(email);
        if (existente.isPresent()) {
            reenviarSePendente(existente.get());
            return;
        }

        Role roleUser = roleRepository.findByNome(RolesEnum.USER)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno: Role USER não encontrada."));
        Optional<Long> idCriado = usuarioRegistrationRepository.insertIfEmailIsFree(
                requestDTO.nome(), email, senhaCodificada, roleUser.getId());
        Usuario usuario = idCriado.isPresent()
                ? usuarioRepository.findById(idCriado.get())
                        .orElseThrow(() -> new IllegalStateException("O usuário cadastrado não foi localizado."))
                : usuarioRepository.findByEmailForUpdate(email)
                        .orElseThrow(() -> new IllegalStateException("O cadastro concorrente não foi localizado."));
        reenviarSePendente(usuario);
    }

    @Transactional
    public void reenviarConfirmacao(ResendEmailConfirmationRequestDTO requestDTO) {
        String email = EmailNormalizer.normalize(requestDTO.email());
        rateLimitService.enforceEmail(AuthRateLimitRoute.RESEND_CONFIRMATION, email);
        usuarioRepository.findByEmailForUpdate(email).ifPresent(this::reenviarSePendente);
    }

    @Transactional
    public void confirmarEmail(EmailConfirmationRequestDTO requestDTO) {
        String token = requestDTO.token();
        if (!token.matches("[A-Za-z0-9_-]{43}")) {
            throw new EmailConfirmationException();
        }

        byte[] tokenHash = sha256(token.getBytes(StandardCharsets.UTF_8));
        Optional<Long> userId = emailConfirmationRepository.findUserIdByHash(tokenHash);
        if (userId.isEmpty()) {
            throw new EmailConfirmationException();
        }

        Usuario usuario = usuarioRepository.findByIdForUpdate(userId.get())
                .orElseThrow(EmailConfirmationException::new);
        rateLimitService.enforceEmail(AuthRateLimitRoute.EMAIL_CONFIRMATION, usuario.getEmail());
        Instant now = applicationClock.instant();
        if (usuario.getEmailConfirmadoEm() != null
                || emailConfirmationRepository.consume(usuario.getId(), tokenHash, now) != 1) {
            throw new EmailConfirmationException();
        }

        usuario.setEmailConfirmadoEm(now);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void recuperarSenha(String requestedEmail) {
        String email = EmailNormalizer.normalize(requestedEmail);
        rateLimitService.enforceEmail(AuthRateLimitRoute.RECOVERY_REQUEST, email);

        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        UUID challengeId = UUID.randomUUID();
        byte[] codeHmac = recoveryHmacService.recoveryCode(challengeId, code);
        Instant now = applicationClock.instant();

        Optional<Usuario> usuarioEncontrado = usuarioRepository.findByEmail(email);
        if (usuarioEncontrado.isEmpty()) {
            return;
        }

        Usuario usuario = usuarioEncontrado.get();
        if (!usuario.isEnabled()) {
            recoveryChallengeRepository.invalidate(usuario.getId(), now);
            return;
        }

        long expirationMinutes = recoveryExpirationProperties.recoveryExpirationMinutes();
        Instant expiresAt = now.plus(Duration.ofMinutes(expirationMinutes));
        recoveryChallengeRepository.replace(usuario.getId(), challengeId, codeHmac, now, expiresAt);
        eventPublisher.publishEvent(new RecoveryEmailRequestedEvent(usuario.getEmail(), code, expirationMinutes));
    }

    @Transactional
    public Optional<Long> redefinirSenha(RedefinirSenhaRequestDTO requestDTO) {
        String email = EmailNormalizer.normalize(requestDTO.email());
        rateLimitService.enforceEmail(AuthRateLimitRoute.RESET_PASSWORD, email);
        passwordPolicy.validate(requestDTO.novaSenha());
        Optional<ValidatedChallenge> desafioEncontrado = validarDesafio(email, requestDTO.codigo());
        if (desafioEncontrado.isEmpty()) {
            return Optional.empty();
        }
        ValidatedChallenge validated = desafioEncontrado.get();

        if (passwordEncoder.matches(requestDTO.novaSenha(), validated.usuario().getPassword())) {
            throw new ValidationConflictException("A nova senha deve ser diferente da atual.");
        }

        validated.usuario().setSenha(passwordEncoder.encode(requestDTO.novaSenha()));
        usuarioRepository.save(validated.usuario());
        recoveryChallengeRepository.consume(validated.usuario().getId(), validated.challenge().id(), applicationClock.instant());
        sessionRevocationService.revogarSessoes(validated.usuario().getId(), null);
        return Optional.of(validated.usuario().getId());
    }

    @Transactional
    public boolean validarCodigo(ValidarCodigoRequestDTO requestDTO) {
        String email = EmailNormalizer.normalize(requestDTO.email());
        rateLimitService.enforceEmail(AuthRateLimitRoute.VALIDATE_RECOVERY, email);
        return validarDesafio(email, requestDTO.codigo()).isPresent();
    }

    private void reenviarSePendente(Usuario usuario) {
        if (usuario.getEmailConfirmadoEm() != null) {
            return;
        }

        Instant now = applicationClock.instant();
        byte[] tokenBytes = new byte[CONFIRMATION_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        byte[] tokenHash = sha256(token.getBytes(StandardCharsets.UTF_8));
        long expirationHours = emailConfirmationProperties.expirationHours();
        emailConfirmationRepository.replace(usuario.getId(), UUID.randomUUID(), tokenHash,
                now, now.plus(Duration.ofHours(expirationHours)));
        String confirmationUrl = emailConfirmationProperties.frontendUrl() + "#token=" + token;
        eventPublisher.publishEvent(new EmailConfirmationRequestedEvent(
                usuario.getEmail(), confirmationUrl, expirationHours));
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível nesta JVM.", exception);
        }
    }

    private Optional<ValidatedChallenge> validarDesafio(String email, String code) {
        Optional<Usuario> usuarioEncontrado = usuarioRepository.findByEmail(email);
        if (usuarioEncontrado.isEmpty()) {
            MessageDigest.isEqual(recoveryHmacService.recoveryCode(DUMMY_CHALLENGE_ID, code), DUMMY_HMAC);
            return Optional.empty();
        }

        Usuario usuario = usuarioEncontrado.get();
        Optional<RecoveryChallengeRepository.RecoveryChallenge> desafioEncontrado =
                recoveryChallengeRepository.findByUserIdForUpdate(usuario.getId());
        if (desafioEncontrado.isEmpty()) {
            MessageDigest.isEqual(recoveryHmacService.recoveryCode(DUMMY_CHALLENGE_ID, code), DUMMY_HMAC);
            return Optional.empty();
        }

        RecoveryChallengeRepository.RecoveryChallenge desafio = desafioEncontrado.get();
        byte[] submittedHmac = recoveryHmacService.recoveryCode(desafio.id(), code);
        boolean codeMatches = MessageDigest.isEqual(submittedHmac, desafio.codeHmac());
        Instant now = applicationClock.instant();
        boolean challengeUsable = usuario.isEnabled()
                && desafio.isUsableAt(now)
                && desafio.attempts() < recoveryProperties.maxAttempts();

        if (!challengeUsable || !codeMatches || !code.matches("[0-9]{6}")) {
            if (challengeUsable) {
                recoveryChallengeRepository.registerInvalidAttempt(
                        usuario.getId(), desafio.id(), now, recoveryProperties.maxAttempts());
            }
            return Optional.empty();
        }

        return Optional.of(new ValidatedChallenge(usuario, desafio));
    }

    private record ValidatedChallenge(Usuario usuario,
                                     RecoveryChallengeRepository.RecoveryChallenge challenge) {
    }
}
