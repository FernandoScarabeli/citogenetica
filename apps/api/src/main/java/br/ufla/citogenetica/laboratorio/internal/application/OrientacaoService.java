package br.ufla.citogenetica.laboratorio.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.ufla.citogenetica.laboratorio.api.event.ConviteOrientacaoCriadoEvent;
import br.ufla.citogenetica.laboratorio.internal.domain.ConviteOrientacao;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoConviteOrientacao;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoParticipacao;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoVinculoOrientacao;
import br.ufla.citogenetica.laboratorio.internal.domain.Laboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.Participacao;
import br.ufla.citogenetica.laboratorio.internal.domain.VinculoOrientacao;
import br.ufla.citogenetica.laboratorio.internal.persistence.ConviteOrientacaoRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.VinculoOrientacaoRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OrientacaoService {

    private static final String CONVITE_INDISPONIVEL = "Convite inválido ou indisponível.";
    private static final String NAO_ENCONTRADO = "Recurso não encontrado.";

    private final LaboratorioAccessPolicy accessPolicy;
    private final ConviteOrientacaoRepository conviteRepository;
    private final VinculoOrientacaoRepository vinculoRepository;
    private final ParticipacaoConviteService participacaoConviteService;
    private final UsuarioConviteQuery usuarioConviteQuery;
    private final UsuarioRosterQuery usuarioRosterQuery;
    private final ConviteLaboratorioRateLimitService rateLimitService;
    private final ConviteTokenService tokenService;
    private final ConviteLaboratorioProperties conviteProperties;
    private final ConviteOrientacaoProperties orientacaoProperties;
    private final ConviteLaboratorioEmailDispatcher emailDispatcher;
    private final UuidFactory uuidFactory;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    public OrientacaoService(LaboratorioAccessPolicy accessPolicy,
                             ConviteOrientacaoRepository conviteRepository,
                             VinculoOrientacaoRepository vinculoRepository,
                             ParticipacaoConviteService participacaoConviteService,
                             UsuarioConviteQuery usuarioConviteQuery,
                             UsuarioRosterQuery usuarioRosterQuery,
                             ConviteLaboratorioRateLimitService rateLimitService,
                             ConviteTokenService tokenService,
                             ConviteLaboratorioProperties conviteProperties,
                             ConviteOrientacaoProperties orientacaoProperties,
                             ConviteLaboratorioEmailDispatcher emailDispatcher,
                             UuidFactory uuidFactory,
                             Clock clock,
                             ApplicationEventPublisher eventPublisher) {
        this.accessPolicy = accessPolicy;
        this.conviteRepository = conviteRepository;
        this.vinculoRepository = vinculoRepository;
        this.participacaoConviteService = participacaoConviteService;
        this.usuarioConviteQuery = usuarioConviteQuery;
        this.usuarioRosterQuery = usuarioRosterQuery;
        this.rateLimitService = rateLimitService;
        this.tokenService = tokenService;
        this.conviteProperties = conviteProperties;
        this.orientacaoProperties = orientacaoProperties;
        this.emailDispatcher = emailDispatcher;
        this.uuidFactory = uuidFactory;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ConviteOrientacaoResumo convidar(UUID laboratorioId, Long orientadorUsuarioId, String email) {
        Laboratorio laboratorio = accessPolicy.bloquearLaboratorio(laboratorioId, orientadorUsuarioId);
        Participacao orientador = exigirPapelOrientador(laboratorioId, orientadorUsuarioId);
        rateLimitService.limitarEnvio(laboratorioId, orientadorUsuarioId);

        String emailNormalizado = usuarioConviteQuery.normalizarEmail(email);
        UsuarioConviteInfo conta = usuarioConviteQuery.buscarPorEmailNormalizado(emailNormalizado).orElse(null);
        if (conta != null && conta.usuarioId().equals(orientadorUsuarioId)) {
            throw new ConflictException("Você não pode enviar um convite de orientação para si mesmo.");
        }
        if (conta != null && vinculoRepository.existsByLaboratorioIdAndOrientadorUsuarioIdAndPesquisadorUsuarioIdAndEstado(
                laboratorioId, orientadorUsuarioId, conta.usuarioId(), EstadoVinculoOrientacao.ATIVO)) {
            throw new ConflictException("Este pesquisador já possui vínculo ativo com você neste laboratório.");
        }

        conviteRepository.findByLaboratorioIdAndOrientadorParticipacaoIdAndEmailDestinoAndEstado(
                        laboratorioId, orientador.getId(), emailNormalizado, EstadoConviteOrientacao.PENDENTE)
                .ifPresent(convite -> {
                    if (convite.getExpiraEm().isAfter(clock.instant())) {
                        throw new ConflictException(
                                "Já existe convite de orientação pendente para este endereço; use o reenvio.");
                    }
                    convite.expirar();
                    conviteRepository.save(convite);
                    entityManager.flush();
                });

        String token = tokenService.gerar();
        Instant expiraEm = clock.instant().plus(conviteProperties.expirationDays(), ChronoUnit.DAYS);
        ConviteOrientacao convite = new ConviteOrientacao(uuidFactory.generate(), laboratorio, orientador,
                emailNormalizado, tokenService.hash(token), expiraEm);
        conviteRepository.save(convite);
        entityManager.flush();
        publicarConvite(laboratorio, convite, token);
        return ConviteOrientacaoResumo.de(convite);
    }

    @Transactional(readOnly = true)
    public List<ConviteOrientacaoResumo> listarConvites(UUID laboratorioId, Long orientadorUsuarioId) {
        Participacao orientador = exigirPapelOrientadorComAcesso(laboratorioId, orientadorUsuarioId);
        Instant agora = clock.instant();
        return conviteRepository.findAllByLaboratorioIdAndOrientadorParticipacaoIdAndEstadoOrderByCreatedAtDesc(
                        laboratorioId, orientador.getId(), EstadoConviteOrientacao.PENDENTE).stream()
                .filter(convite -> convite.getExpiraEm().isAfter(agora))
                .map(ConviteOrientacaoResumo::de)
                .toList();
    }

    @Transactional
    public ConviteOrientacaoResumo reenviarConvite(UUID laboratorioId, UUID conviteId, Long orientadorUsuarioId) {
        Laboratorio laboratorio = accessPolicy.bloquearLaboratorio(laboratorioId, orientadorUsuarioId);
        Participacao orientador = exigirPapelOrientador(laboratorioId, orientadorUsuarioId);
        rateLimitService.limitarEnvio(laboratorioId, orientadorUsuarioId);
        ConviteOrientacao convite = buscarConviteBloqueado(laboratorioId, conviteId);
        exigirPropriedade(convite, orientador, laboratorioId);

        String token = tokenService.gerar();
        Instant expiraEm = clock.instant().plus(conviteProperties.expirationDays(), ChronoUnit.DAYS);
        if (convite.getEstado() == EstadoConviteOrientacao.PENDENTE
                && !convite.getExpiraEm().isAfter(clock.instant())) {
            convite.expirar();
        }
        try {
            convite.reenviar(tokenService.hash(token), expiraEm);
        } catch (IllegalStateException exception) {
            throw new ConflictException("O convite de orientação não pode ser reenviado.");
        }
        conviteRepository.save(convite);
        entityManager.flush();
        publicarConvite(laboratorio, convite, token);
        return ConviteOrientacaoResumo.de(convite);
    }

    @Transactional
    public void revogarConvite(UUID laboratorioId, UUID conviteId, Long orientadorUsuarioId) {
        accessPolicy.bloquearLaboratorio(laboratorioId);
        Participacao orientador = exigirPapelOrientadorComAcesso(laboratorioId, orientadorUsuarioId);
        ConviteOrientacao convite = buscarConviteBloqueado(laboratorioId, conviteId);
        exigirPropriedade(convite, orientador, laboratorioId);
        try {
            convite.revogar(orientadorUsuarioId, clock.instant());
        } catch (IllegalStateException exception) {
            throw new ConflictException("O convite de orientação não está mais pendente.");
        }
    }

    @Transactional(readOnly = true)
    public List<VinculoOrientacaoResumo> listarVinculos(UUID laboratorioId, Long usuarioId) {
        accessPolicy.exigirAcesso(laboratorioId, usuarioId);
        Participacao participacao = accessPolicy.exigirParticipacaoAtiva(laboratorioId, usuarioId);
        boolean administrador = accessPolicy.papeisAtivos(participacao.getId()).contains(PapelLaboratorio.ADMIN);
        List<VinculoOrientacao> vinculos = administrador
                ? vinculoRepository.findAllByLaboratorioIdAndEstadoOrderByCreatedAtDesc(
                        laboratorioId, EstadoVinculoOrientacao.ATIVO)
                : vinculoRepository.listarVisiveisDaParticipacao(
                        laboratorioId, participacao.getId(), EstadoVinculoOrientacao.ATIVO);
        if (vinculos.isEmpty()) {
            return List.of();
        }

        List<Long> usuarioIds = vinculos.stream()
                .flatMap(vinculo -> java.util.stream.Stream.of(
                        vinculo.getOrientadorUsuarioId(), vinculo.getPesquisadorUsuarioId()))
                .distinct().toList();
        Map<Long, UsuarioRosterInfo> usuarios = usuarioRosterQuery.buscarPorIds(usuarioIds).stream()
                .collect(Collectors.toMap(UsuarioRosterInfo::usuarioId, Function.identity()));
        return vinculos.stream()
                .map(vinculo -> VinculoOrientacaoResumo.de(vinculo,
                        nomeUsuario(usuarios, vinculo.getOrientadorUsuarioId()),
                        nomeUsuario(usuarios, vinculo.getPesquisadorUsuarioId())))
                .toList();
    }

    @Transactional
    public void encerrarVinculo(UUID laboratorioId, UUID vinculoId, Long operadorId) {
        // Laboratório desativado não aceita alterações: encerrar vínculo responde 409 como as demais mutações.
        accessPolicy.bloquearLaboratorio(laboratorioId, operadorId);
        Participacao operador = accessPolicy.exigirParticipacaoAtiva(laboratorioId, operadorId);
        VinculoOrientacao vinculo = vinculoRepository.findByIdAndLaboratorioIdForUpdate(vinculoId, laboratorioId)
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
        if (vinculo.getEstado() != EstadoVinculoOrientacao.ATIVO) {
            throw new ConflictException("O vínculo de orientação não está ativo.");
        }

        Set<PapelLaboratorio> papeis = accessPolicy.papeisAtivos(operador.getId());
        boolean administrador = papeis.contains(PapelLaboratorio.ADMIN);
        boolean orientadorDoVinculo = vinculo.getOrientadorUsuarioId().equals(operadorId)
                && papeis.contains(PapelLaboratorio.ORIENTADOR);
        if (!administrador && !orientadorDoVinculo) {
            throw new AccessDeniedException("Acesso negado.");
        }
        try {
            vinculo.encerrar(operadorId, clock.instant());
        } catch (IllegalStateException exception) {
            throw new ConflictException("O vínculo de orientação não está ativo.");
        }
    }

    /** Pública: quem tem o link vê o nome do laboratório e do orientador, sem precisar entrar. */
    @Transactional(readOnly = true)
    public PreviaConvite previa(String token, String enderecoIp, String emailSessao) {
        rateLimitService.limitarPreviaPorIp(enderecoIp);
        byte[] hash = tokenService.hash(token);
        ConviteOrientacao convite = buscarPorToken(hash);
        if (convite.getEstado() != EstadoConviteOrientacao.PENDENTE
                || !tokenService.iguais(convite.getTokenHash(), hash)
                || convite.getLaboratorio().getEstado() != EstadoLaboratorio.ATIVO
                || !orientadorAindaPodeConvidar(convite)) {
            throw new BadRequestException(CONVITE_INDISPONIVEL);
        }
        String orientador = usuarioRosterQuery.buscarPorIds(List.of(convite.getOrientadorUsuarioId())).stream()
                .findFirst().map(UsuarioRosterInfo::nome).orElseThrow(() -> new BadRequestException(CONVITE_INDISPONIVEL));
        PreviaConvite.Estado estado = convite.getExpiraEm().isAfter(clock.instant())
                ? PreviaConvite.Estado.PENDENTE : PreviaConvite.Estado.EXPIRADO;
        return new PreviaConvite(convite.getLaboratorio().getNome(), orientador,
                EmailMascarado.de(convite.getEmailDestino()), List.of(), estado, paraContaAtual(convite.getEmailDestino(), emailSessao));
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
    private void concluirAceite(ConviteOrientacao referencia, byte[] hash, Long usuarioId, String emailSessao) {
        Laboratorio laboratorio = accessPolicy.bloquearLaboratorio(referencia.getLaboratorio().getId());
        ConviteOrientacao convite = buscarConviteBloqueado(referencia.getLaboratorio().getId(), referencia.getId());
        validarTokenPendente(convite, hash);
        validarDestinatario(convite.getEmailDestino(), usuarioId, emailSessao);
        if (laboratorio.getEstado() != EstadoLaboratorio.ATIVO || !orientadorAindaPodeConvidar(convite)
                || convite.getOrientadorUsuarioId().equals(usuarioId)) {
            throw new BadRequestException(CONVITE_INDISPONIVEL);
        }

        if (vinculoRepository.existsByLaboratorioIdAndOrientadorUsuarioIdAndPesquisadorUsuarioIdAndEstado(
                laboratorio.getId(), convite.getOrientadorUsuarioId(), usuarioId, EstadoVinculoOrientacao.ATIVO)) {
            throw new BadRequestException(CONVITE_INDISPONIVEL);
        }
        Participacao pesquisador = participacaoConviteService.garantirParticipacaoAtiva(
                laboratorio, usuarioId, convite.getOrientadorUsuarioId(), Set.of(PapelLaboratorio.MEMBRO));
        VinculoOrientacao vinculo = new VinculoOrientacao(uuidFactory.generate(), laboratorio,
                convite.getOrientadorParticipacao(), pesquisador, convite.getOrientadorUsuarioId());
        vinculoRepository.save(vinculo);
        entityManager.flush();
        try {
            convite.aceitar(usuarioId, vinculo, clock.instant());
            conviteRepository.save(convite);
        } catch (IllegalStateException exception) {
            throw new BadRequestException(CONVITE_INDISPONIVEL);
        }
    }

    @Transactional
    public void recusar(String token, Long usuarioId, String emailSessao, String enderecoIp) {
        limitarConsumo(usuarioId, enderecoIp);
        byte[] hash = tokenService.hash(token);
        concluirRecusa(buscarPorToken(hash), hash, usuarioId, emailSessao);
    }

    private void concluirRecusa(ConviteOrientacao referencia, byte[] hash, Long usuarioId, String emailSessao) {
        Laboratorio laboratorio = accessPolicy.bloquearLaboratorio(referencia.getLaboratorio().getId());
        ConviteOrientacao convite = buscarConviteBloqueado(referencia.getLaboratorio().getId(), referencia.getId());
        validarTokenPendente(convite, hash);
        validarDestinatario(convite.getEmailDestino(), usuarioId, emailSessao);
        if (laboratorio.getEstado() != EstadoLaboratorio.ATIVO || !orientadorAindaPodeConvidar(convite)
                || convite.getOrientadorUsuarioId().equals(usuarioId)) {
            throw new BadRequestException(CONVITE_INDISPONIVEL);
        }
        try {
            convite.recusar(usuarioId, clock.instant());
            conviteRepository.save(convite);
        } catch (IllegalStateException exception) {
            throw new BadRequestException(CONVITE_INDISPONIVEL);
        }
    }

    private Participacao exigirPapelOrientador(UUID laboratorioId, Long usuarioId) {
        Participacao participacao = accessPolicy.exigirParticipacaoAtiva(laboratorioId, usuarioId);
        if (!accessPolicy.papeisAtivos(participacao.getId()).contains(PapelLaboratorio.ORIENTADOR)) {
            throw new AccessDeniedException("Acesso negado.");
        }
        return participacao;
    }

    private Participacao exigirPapelOrientadorComAcesso(UUID laboratorioId, Long usuarioId) {
        accessPolicy.exigirAcesso(laboratorioId, usuarioId);
        return exigirPapelOrientador(laboratorioId, usuarioId);
    }

    /** Sem sessão não há como saber: devolve nulo. Usa a mesma normalização do aceite. */
    private Boolean paraContaAtual(String emailDestino, String emailSessao) {
        return emailSessao == null ? null : emailDestino.equals(usuarioConviteQuery.normalizarEmail(emailSessao));
    }

    private void limitarConsumo(Long usuarioId, String enderecoIp) {
        rateLimitService.limitarAceitePorIp(enderecoIp);
        rateLimitService.limitarAceitePorUsuario(usuarioId);
    }

    private ConviteOrientacao buscarPorToken(byte[] hash) {
        if (hash == null) {
            throw new BadRequestException(CONVITE_INDISPONIVEL);
        }
        return conviteRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadRequestException(CONVITE_INDISPONIVEL));
    }

    private ConviteOrientacao buscarPorId(UUID conviteId) {
        return conviteRepository.findById(conviteId)
                .orElseThrow(() -> new BadRequestException(CONVITE_INDISPONIVEL));
    }

    private ConviteOrientacao buscarConviteBloqueado(UUID laboratorioId, UUID conviteId) {
        ConviteOrientacao convite = conviteRepository.findByIdForUpdate(conviteId)
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
        entityManager.refresh(convite, LockModeType.PESSIMISTIC_WRITE);
        if (!convite.getLaboratorio().getId().equals(laboratorioId)) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }
        return convite;
    }

    private void exigirPropriedade(ConviteOrientacao convite, Participacao orientador, UUID laboratorioId) {
        if (!convite.getLaboratorio().getId().equals(laboratorioId)
                || !convite.getOrientadorParticipacao().getId().equals(orientador.getId())) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }
    }

    private void validarTokenPendente(ConviteOrientacao convite, byte[] hashRecebido) {
        if (convite.getEstado() != EstadoConviteOrientacao.PENDENTE
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

    private boolean orientadorAindaPodeConvidar(ConviteOrientacao convite) {
        Participacao participacao = convite.getOrientadorParticipacao();
        return participacao.getEstado() == EstadoParticipacao.ATIVA
                && participacao.getLaboratorio().getId().equals(convite.getLaboratorio().getId())
                && accessPolicy.papeisAtivos(participacao.getId()).contains(PapelLaboratorio.ORIENTADOR);
    }

    private void publicarConvite(Laboratorio laboratorio, ConviteOrientacao convite, String token) {
        eventPublisher.publishEvent(new ConviteOrientacaoCriadoEvent(convite.getId(), laboratorio.getId(),
                convite.getOrientadorUsuarioId(), laboratorio.getNome(), convite.getEmailDestino()));
        String link = orientacaoProperties.frontendUrl() + "#token=" + token;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                emailDispatcher.enviarOrientacao(convite.getId(), convite.getEmailDestino(),
                        laboratorio.getNome(), link, conviteProperties.expirationDays());
            }
        });
    }

    private String nomeUsuario(Map<Long, UsuarioRosterInfo> usuarios, Long usuarioId) {
        UsuarioRosterInfo usuario = usuarios.get(usuarioId);
        if (usuario == null) {
            throw new IllegalStateException("Conta vinculada à orientação não foi encontrada.");
        }
        return usuario.nome();
    }
}
