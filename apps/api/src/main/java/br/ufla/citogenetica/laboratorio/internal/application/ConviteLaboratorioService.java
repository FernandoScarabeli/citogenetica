package br.ufla.citogenetica.laboratorio.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.ufla.citogenetica.laboratorio.api.event.ConviteLaboratorioCriadoEvent;
import br.ufla.citogenetica.laboratorio.internal.domain.ConviteLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoConviteLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoParticipacao;
import br.ufla.citogenetica.laboratorio.internal.domain.Laboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.Participacao;
import br.ufla.citogenetica.laboratorio.internal.persistence.ConviteLaboratorioRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoRepository;
import br.ufla.citogenetica.shared.persistence.UuidFactory;
import br.ufla.citogenetica.shared.web.error.BadRequestException;
import br.ufla.citogenetica.shared.web.error.ConflictException;
import br.ufla.citogenetica.shared.web.error.NotFoundException;
import br.ufla.citogenetica.usuario.api.UsuarioConviteInfo;
import br.ufla.citogenetica.usuario.api.UsuarioConviteQuery;
import br.ufla.citogenetica.usuario.api.UsuarioRosterInfo;
import br.ufla.citogenetica.usuario.api.UsuarioRosterQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ConviteLaboratorioService {

    private static final String CONVITE_INDISPONIVEL = "Convite inválido ou indisponível.";
    private static final String NAO_ENCONTRADO = "Convite não encontrado.";

    private final LaboratorioAccessPolicy accessPolicy;
    private final ConviteLaboratorioRepository conviteRepository;
    private final ParticipacaoRepository participacaoRepository;
    private final ParticipacaoConviteService participacaoConviteService;
    private final UsuarioConviteQuery usuarioConviteQuery;
    private final UsuarioRosterQuery usuarioRosterQuery;
    private final ConviteLaboratorioRateLimitService rateLimitService;
    private final ConviteTokenService tokenService;
    private final ConviteLaboratorioProperties properties;
    private final ConviteLaboratorioEmailDispatcher emailDispatcher;
    private final UuidFactory uuidFactory;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    public ConviteLaboratorioService(LaboratorioAccessPolicy accessPolicy,
                                     ConviteLaboratorioRepository conviteRepository,
                                     ParticipacaoRepository participacaoRepository,
                                     ParticipacaoConviteService participacaoConviteService,
                                     UsuarioConviteQuery usuarioConviteQuery,
                                     UsuarioRosterQuery usuarioRosterQuery,
                                     ConviteLaboratorioRateLimitService rateLimitService,
                                     ConviteTokenService tokenService,
                                     ConviteLaboratorioProperties properties,
                                     ConviteLaboratorioEmailDispatcher emailDispatcher,
                                     UuidFactory uuidFactory,
                                     Clock clock,
                                     ApplicationEventPublisher eventPublisher) {
        this.accessPolicy = accessPolicy;
        this.conviteRepository = conviteRepository;
        this.participacaoRepository = participacaoRepository;
        this.participacaoConviteService = participacaoConviteService;
        this.usuarioConviteQuery = usuarioConviteQuery;
        this.usuarioRosterQuery = usuarioRosterQuery;
        this.rateLimitService = rateLimitService;
        this.tokenService = tokenService;
        this.properties = properties;
        this.emailDispatcher = emailDispatcher;
        this.uuidFactory = uuidFactory;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ConviteLaboratorioResumo criar(UUID laboratorioId, Long autorId, String email,
                                          Set<PapelLaboratorio> papeis) {
        Laboratorio laboratorio = accessPolicy.bloquearLaboratorioParaAdmin(laboratorioId, autorId);
        rateLimitService.limitarEnvio(laboratorioId, autorId);
        String emailNormalizado = usuarioConviteQuery.normalizarEmail(email);

        usuarioConviteQuery.buscarPorEmailNormalizado(emailNormalizado).ifPresent(destinatario -> {
            if (participacaoRepository.existsByLaboratorioIdAndUsuarioIdAndEstado(
                    laboratorioId, destinatario.usuarioId(), EstadoParticipacao.ATIVA)) {
                throw new ConflictException("Este endereço já participa ativamente do laboratório.");
            }
        });

        conviteRepository.findByLaboratorioIdAndEmailDestinoAndEstado(
                        laboratorioId, emailNormalizado, EstadoConviteLaboratorio.PENDENTE)
                .ifPresent(convite -> {
                    if (convite.getExpiraEm().isAfter(clock.instant())) {
                        throw new ConflictException("Já existe convite pendente para este endereço; use o reenvio.");
                    }
                    convite.expirar();
                    conviteRepository.save(convite);
                    entityManager.flush();
                });

        String token = tokenService.gerar();
        Instant expiraEm = clock.instant().plus(properties.expirationDays(), ChronoUnit.DAYS);
        ConviteLaboratorio convite = new ConviteLaboratorio(uuidFactory.generate(), laboratorio, autorId,
                emailNormalizado, tokenService.hash(token), papeis.contains(PapelLaboratorio.ADMIN),
                papeis.contains(PapelLaboratorio.ORIENTADOR), expiraEm);
        conviteRepository.save(convite);
        entityManager.flush();
        publicarConvite(laboratorio, convite, token);
        return ConviteLaboratorioResumo.de(convite);
    }

    @Transactional(readOnly = true)
    public List<ConviteLaboratorioResumo> listar(UUID laboratorioId, Long usuarioId) {
        Laboratorio laboratorio = accessPolicy.exigirAcesso(laboratorioId, usuarioId);
        accessPolicy.exigirAdmin(laboratorio.getId(), usuarioId);
        Instant agora = clock.instant();
        return conviteRepository.findAllByLaboratorioIdAndEstadoOrderByCreatedAtDesc(
                        laboratorioId, EstadoConviteLaboratorio.PENDENTE).stream()
                .filter(convite -> convite.getExpiraEm().isAfter(agora))
                .map(ConviteLaboratorioResumo::de)
                .toList();
    }

    @Transactional
    public ConviteLaboratorioResumo reenviar(UUID laboratorioId, UUID conviteId, Long autorId) {
        Laboratorio laboratorio = accessPolicy.bloquearLaboratorioParaAdmin(laboratorioId, autorId);
        rateLimitService.limitarEnvio(laboratorioId, autorId);
        ConviteLaboratorio convite = buscarConviteBloqueado(laboratorioId, conviteId);
        String token = tokenService.gerar();
        Instant expiraEm = clock.instant().plus(properties.expirationDays(), ChronoUnit.DAYS);
        try {
            convite.reenviar(tokenService.hash(token), expiraEm);
        } catch (IllegalStateException exception) {
            throw new ConflictException("O convite não pode ser reenviado.");
        }
        conviteRepository.save(convite);
        entityManager.flush();
        publicarConvite(laboratorio, convite, token);
        return ConviteLaboratorioResumo.de(convite);
    }

    @Transactional
    public void revogar(UUID laboratorioId, UUID conviteId, Long autorId) {
        accessPolicy.bloquearLaboratorioParaAdminSemExigirAtivo(laboratorioId, autorId);
        ConviteLaboratorio convite = buscarConviteBloqueado(laboratorioId, conviteId);
        try {
            convite.revogar(autorId, clock.instant());
        } catch (IllegalStateException exception) {
            throw new ConflictException("O convite não está mais pendente.");
        }
    }

    /** Pública: quem tem o link vê o nome do laboratório e de quem convidou, sem precisar entrar. */
    @Transactional(readOnly = true)
    public PreviaConvite previa(String token, String enderecoIp, String emailSessao) {
        rateLimitService.limitarPreviaPorIp(enderecoIp);
        byte[] hash = tokenService.hash(token);
        ConviteLaboratorio convite = buscarPorToken(hash);
        if (convite.getEstado() != EstadoConviteLaboratorio.PENDENTE
                || !tokenService.iguais(convite.getTokenHash(), hash)
                || convite.getLaboratorio().getEstado() != EstadoLaboratorio.ATIVO) {
            throw new BadRequestException(CONVITE_INDISPONIVEL);
        }
        String convidadoPor = usuarioRosterQuery.buscarPorIds(List.of(convite.getConvidadoPorUsuarioId())).stream()
                .findFirst().map(UsuarioRosterInfo::nome).orElseThrow(() -> new BadRequestException(CONVITE_INDISPONIVEL));
        List<String> papeis = papeisConcedidos(convite).stream()
                .sorted(Comparator.comparing(PapelLaboratorio::name)).map(Enum::name).toList();
        PreviaConvite.Estado estado = convite.getExpiraEm().isAfter(clock.instant())
                ? PreviaConvite.Estado.PENDENTE : PreviaConvite.Estado.EXPIRADO;
        return new PreviaConvite(convite.getLaboratorio().getNome(), convidadoPor,
                EmailMascarado.de(convite.getEmailDestino()), papeis, estado, paraContaAtual(convite.getEmailDestino(), emailSessao));
    }

    @Transactional
    public void aceitar(String token, Long usuarioId, String emailSessao, String enderecoIp) {
        limitarConsumo(usuarioId, enderecoIp);
        byte[] hash = tokenService.hash(token);
        concluirAceite(buscarPorToken(hash), hash, usuarioId, emailSessao);
    }

    /**
     * Resposta pelo sino de notificações. A sessão do próprio destinatário (e-mail igual, conta ativa e confirmada)
     * prova o mesmo que o link do e-mail: que quem responde é dono do endereço convidado.
     */
    @Transactional
    public void aceitarPelaNotificacao(UUID conviteId, Long usuarioId, String emailSessao, String enderecoIp) {
        limitarConsumo(usuarioId, enderecoIp);
        concluirAceite(buscarPorId(conviteId), null, usuarioId, emailSessao);
    }

    @Transactional
    public void recusarPelaNotificacao(UUID conviteId, Long usuarioId, String emailSessao, String enderecoIp) {
        limitarConsumo(usuarioId, enderecoIp);
        concluirRecusa(buscarPorId(conviteId), null, usuarioId, emailSessao);
    }

    /** {@code hash} nulo quando a resposta vem pelo sino, sem o link do e-mail. */
    private void concluirAceite(ConviteLaboratorio referencia, byte[] hash, Long usuarioId, String emailSessao) {
        Laboratorio laboratorio = accessPolicy.bloquearLaboratorio(referencia.getLaboratorio().getId());
        ConviteLaboratorio convite = buscarConviteBloqueado(referencia.getLaboratorio().getId(), referencia.getId());
        validarTokenPendente(convite, hash);
        validarDestinatario(convite.getEmailDestino(), usuarioId, emailSessao);
        // Verificado só depois do token e do destinatário: a resposta genérica não revela o estado do laboratório.
        if (laboratorio.getEstado() != EstadoLaboratorio.ATIVO) {
            throw new BadRequestException(CONVITE_INDISPONIVEL);
        }

        EnumSet<PapelLaboratorio> papeis = papeisConcedidos(convite);
        Participacao participacao = participacaoConviteService.garantirParticipacaoAtiva(
                laboratorio, usuarioId, convite.getConvidadoPorUsuarioId(), papeis);
        convite.aceitar(usuarioId, participacao.getId(), clock.instant());
        conviteRepository.save(convite);
    }

    @Transactional
    public void recusar(String token, Long usuarioId, String emailSessao, String enderecoIp) {
        limitarConsumo(usuarioId, enderecoIp);
        byte[] hash = tokenService.hash(token);
        concluirRecusa(buscarPorToken(hash), hash, usuarioId, emailSessao);
    }

    private void concluirRecusa(ConviteLaboratorio referencia, byte[] hash, Long usuarioId, String emailSessao) {
        accessPolicy.bloquearLaboratorio(referencia.getLaboratorio().getId());
        ConviteLaboratorio convite = buscarConviteBloqueado(referencia.getLaboratorio().getId(), referencia.getId());
        validarTokenPendente(convite, hash);
        validarDestinatario(convite.getEmailDestino(), usuarioId, emailSessao);
        convite.recusar(usuarioId, clock.instant());
        conviteRepository.save(convite);
    }

    /** Sem sessão não há como saber: devolve nulo. Usa a mesma normalização do aceite. */
    private Boolean paraContaAtual(String emailDestino, String emailSessao) {
        return emailSessao == null ? null : emailDestino.equals(usuarioConviteQuery.normalizarEmail(emailSessao));
    }

    private void limitarConsumo(Long usuarioId, String enderecoIp) {
        rateLimitService.limitarAceitePorIp(enderecoIp);
        rateLimitService.limitarAceitePorUsuario(usuarioId);
    }

    private ConviteLaboratorio buscarPorToken(byte[] hash) {
        if (hash == null) {
            throw new BadRequestException(CONVITE_INDISPONIVEL);
        }
        return conviteRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadRequestException(CONVITE_INDISPONIVEL));
    }

    private ConviteLaboratorio buscarPorId(UUID conviteId) {
        return conviteRepository.findById(conviteId)
                .orElseThrow(() -> new BadRequestException(CONVITE_INDISPONIVEL));
    }

    private ConviteLaboratorio buscarConviteBloqueado(UUID laboratorioId, UUID conviteId) {
        ConviteLaboratorio convite = conviteRepository.findByIdForUpdate(conviteId)
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
        entityManager.refresh(convite, LockModeType.PESSIMISTIC_WRITE);
        if (!convite.getLaboratorio().getId().equals(laboratorioId)) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }
        return convite;
    }

    private void validarTokenPendente(ConviteLaboratorio convite, byte[] hashRecebido) {
        if (convite.getEstado() != EstadoConviteLaboratorio.PENDENTE
                || (hashRecebido != null && !tokenService.iguais(convite.getTokenHash(), hashRecebido))
                || !convite.getExpiraEm().isAfter(clock.instant())) {
            throw new BadRequestException(CONVITE_INDISPONIVEL);
        }
    }

    private void validarDestinatario(String emailDestino, Long usuarioId, String emailSessao) {
        String emailNormalizadoSessao = usuarioConviteQuery.normalizarEmail(emailSessao);
        if (!emailDestino.equals(emailNormalizadoSessao)) {
            throw new BadRequestException(CONVITE_INDISPONIVEL);
        }
        UsuarioConviteInfo destinatario = usuarioConviteQuery.bloquearPorEmailNormalizado(emailDestino)
                .orElseThrow(() -> new BadRequestException(CONVITE_INDISPONIVEL));
        if (!destinatario.usuarioId().equals(usuarioId) || !destinatario.ativo() || !destinatario.emailConfirmado()) {
            throw new BadRequestException(CONVITE_INDISPONIVEL);
        }
    }

    private EnumSet<PapelLaboratorio> papeisConcedidos(ConviteLaboratorio convite) {
        EnumSet<PapelLaboratorio> papeis = EnumSet.of(PapelLaboratorio.MEMBRO);
        if (convite.isConcederAdmin()) {
            papeis.add(PapelLaboratorio.ADMIN);
        }
        if (convite.isConcederOrientador()) {
            papeis.add(PapelLaboratorio.ORIENTADOR);
        }
        return papeis;
    }

    private void publicarConvite(Laboratorio laboratorio, ConviteLaboratorio convite, String token) {
        Set<String> papeis = papeisConcedidos(convite).stream()
                .sorted(Comparator.comparing(PapelLaboratorio::name))
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
        eventPublisher.publishEvent(new ConviteLaboratorioCriadoEvent(convite.getId(), laboratorio.getId(),
                convite.getConvidadoPorUsuarioId(), laboratorio.getNome(), convite.getEmailDestino(), papeis));

        String link = properties.frontendUrl() + "#token=" + token;
        List<String> papeisLegiveis = papeisConcedidos(convite).stream()
                .sorted(Comparator.comparing(PapelLaboratorio::name))
                .map(this::nomePapel)
                .toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                emailDispatcher.enviar(convite.getId(), convite.getEmailDestino(), laboratorio.getNome(),
                        papeisLegiveis, link, properties.expirationDays());
            }
        });
    }

    private String nomePapel(PapelLaboratorio papel) {
        return switch (papel) {
            case MEMBRO -> "Participante";
            case ADMIN -> "Administrador do laboratório";
            case ORIENTADOR -> "Orientador";
        };
    }
}
