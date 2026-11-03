package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.usuario.api.PlatformAdminBootstrap;
import br.ufla.citogenetica.usuario.api.RolesEnum;
import br.ufla.citogenetica.usuario.api.error.PlatformAdminBootstrapException;
import br.ufla.citogenetica.usuario.internal.domain.EmailNormalizer;
import br.ufla.citogenetica.usuario.internal.domain.Role;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.domain.event.PlatformAdminBootstrapCompletedEvent;
import br.ufla.citogenetica.usuario.internal.persistence.PlatformAdminBootstrapRepository;
import br.ufla.citogenetica.usuario.internal.persistence.RoleRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class PlatformAdminBootstrapService implements PlatformAdminBootstrap {

    private final PlatformAdminBootstrapRepository bootstrapRepository;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final SessionRevocationService sessionRevocationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PlatformAdminBootstrapService(PlatformAdminBootstrapRepository bootstrapRepository,
                                         UsuarioRepository usuarioRepository,
                                         RoleRepository roleRepository,
                                         SessionRevocationService sessionRevocationService,
                                         ApplicationEventPublisher eventPublisher,
                                         Clock clock) {
        this.bootstrapRepository = bootstrapRepository;
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
        this.sessionRevocationService = sessionRevocationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public long promoteFirstAdmin(String requestedEmail) {
        String email = EmailNormalizer.normalize(requestedEmail);
        if (email == null || email.isBlank()) {
            throw new PlatformAdminBootstrapException("Informe o e-mail da conta que deve receber o primeiro papel ADMIN.");
        }

        if (bootstrapRepository.lockAndIsConsumed()) {
            throw new PlatformAdminBootstrapException("O bootstrap do primeiro administrador já foi consumido.");
        }

        Usuario usuario = usuarioRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new PlatformAdminBootstrapException(
                        "A conta informada não existe. Cadastre e confirme o e-mail antes do bootstrap."));
        if (usuario.getStatus() != StatusEnum.ATIVO) {
            throw new PlatformAdminBootstrapException("A conta informada está inativa e não pode ser promovida.");
        }
        if (usuario.getEmailConfirmadoEm() == null) {
            throw new PlatformAdminBootstrapException("A conta informada ainda não confirmou o e-mail.");
        }

        Role adminRole = roleRepository.findByNome(RolesEnum.ADMIN)
                .orElseThrow(() -> new PlatformAdminBootstrapException(
                        "O papel ADMIN não existe no banco de dados."));
        usuario.setRole(adminRole);
        usuarioRepository.save(usuario);
        sessionRevocationService.revogarSessoes(usuario.getId(), null);
        bootstrapRepository.consume(clock.instant(), usuario.getId());
        eventPublisher.publishEvent(new PlatformAdminBootstrapCompletedEvent(usuario.getId()));
        return usuario.getId();
    }
}
