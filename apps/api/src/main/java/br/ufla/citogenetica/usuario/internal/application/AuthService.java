package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.shared.web.error.BusinessException;
import br.ufla.citogenetica.usuario.api.error.ValidationConflictException;
import br.ufla.citogenetica.usuario.internal.mapper.UsuarioMapper;
import br.ufla.citogenetica.usuario.internal.domain.Role;
import br.ufla.citogenetica.usuario.api.RolesEnum;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.usuario.internal.domain.EmailNormalizer;
import br.ufla.citogenetica.usuario.internal.domain.event.RecoveryEmailRequestedEvent;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.web.dto.request.LoginRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.RedefinirSenhaRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.UsuarioRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.ValidarCodigoRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.response.UsuarioResponseDTO;
import br.ufla.citogenetica.usuario.internal.persistence.RecoveryChallengeRepository;
import br.ufla.citogenetica.usuario.internal.persistence.RoleRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final UUID DUMMY_CHALLENGE_ID = new UUID(0, 0);
    private static final byte[] DUMMY_HMAC = new byte[32];

    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final RecoveryChallengeRepository recoveryChallengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UsuarioMapper usuarioMapper;
    private final RecoveryHmacService recoveryHmacService;
    private final RecoveryProperties recoveryProperties;
    private final RecoveryExpirationProperties recoveryExpirationProperties;
    private final RateLimitService rateLimitService;
    private final SecureRandom secureRandom;
    private final Clock applicationClock;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionRevocationService sessionRevocationService;

    public Authentication login(LoginRequestDTO requestDTO) {
        String email = EmailNormalizer.normalize(requestDTO.email());
        rateLimitService.enforceEmail(AuthRateLimitRoute.LOGIN, email);
        var usernamePassword = new UsernamePasswordAuthenticationToken(email, requestDTO.senha());
        return this.authenticationManager.authenticate(usernamePassword);
    }

    @Transactional
    public UsuarioResponseDTO register(UsuarioRequestDTO requestDTO) {
        String email = EmailNormalizer.normalize(requestDTO.email());
        if (this.usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new ValidationConflictException("E-mail já cadastrado.");
        }

        Usuario usuario = this.usuarioMapper.requestToEntity(requestDTO);
        usuario.setEmail(email);

        String roleSolicitada = requestDTO.roleName().trim().toUpperCase(Locale.ROOT);
        RolesEnum roleEnum;
        try {
            roleEnum = RolesEnum.valueOf(roleSolicitada);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("Role inválida: " + roleSolicitada);
        }

        if (roleEnum != RolesEnum.USER) {
            throw new BusinessException("Não é permitido registrar um usuário com a role: " + roleSolicitada);
        }

        Role role = this.roleRepository.findByNome(roleEnum)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Erro interno: Role " + roleEnum.name() + " não encontrada."));
        usuario.setRole(role);
        usuario.setSenha(this.passwordEncoder.encode(requestDTO.senha()));
        usuario.setStatus(StatusEnum.ATIVO);

        this.usuarioRepository.save(usuario);
        return this.usuarioMapper.toResponseDTO(usuario);
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
        if (usuario.getStatus() != StatusEnum.ATIVO) {
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
        boolean challengeUsable = usuario.getStatus() == StatusEnum.ATIVO
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
