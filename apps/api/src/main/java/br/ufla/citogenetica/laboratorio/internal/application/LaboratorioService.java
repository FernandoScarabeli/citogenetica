package br.ufla.citogenetica.laboratorio.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.ufla.citogenetica.laboratorio.internal.domain.EstadoLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoParticipacao;
import br.ufla.citogenetica.laboratorio.internal.domain.Laboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.Participacao;
import br.ufla.citogenetica.laboratorio.internal.domain.ParticipacaoPapel;
import br.ufla.citogenetica.laboratorio.internal.persistence.LaboratorioRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoPapelRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoRepository;
import br.ufla.citogenetica.shared.persistence.UuidFactory;
import br.ufla.citogenetica.shared.web.error.ConflictException;
import br.ufla.citogenetica.shared.web.error.NotFoundException;
import br.ufla.citogenetica.usuario.api.UsuarioRosterInfo;
import br.ufla.citogenetica.usuario.api.UsuarioRosterQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
public class LaboratorioService {

    private static final String NAO_ENCONTRADO = "Laboratório não encontrado.";
    private static final String CONFLITO_GENERICO = "A operação não pode ser concluída.";

    private final LaboratorioRepository laboratorioRepository;
    private final ParticipacaoRepository participacaoRepository;
    private final ParticipacaoPapelRepository papelRepository;
    private final LaboratorioAccessPolicy accessPolicy;
    private final OrientacaoLifecycleService orientacaoLifecycleService;
    private final UsuarioRosterQuery usuarioRosterQuery;
    private final UuidFactory uuidFactory;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public LaboratorioService(LaboratorioRepository laboratorioRepository,
                              ParticipacaoRepository participacaoRepository,
                              ParticipacaoPapelRepository papelRepository,
                              LaboratorioAccessPolicy accessPolicy,
                              OrientacaoLifecycleService orientacaoLifecycleService,
                              UsuarioRosterQuery usuarioRosterQuery,
                              UuidFactory uuidFactory,
                              Clock clock) {
        this.laboratorioRepository = laboratorioRepository;
        this.participacaoRepository = participacaoRepository;
        this.papelRepository = papelRepository;
        this.accessPolicy = accessPolicy;
        this.orientacaoLifecycleService = orientacaoLifecycleService;
        this.usuarioRosterQuery = usuarioRosterQuery;
        this.uuidFactory = uuidFactory;
        this.clock = clock;
    }

    @Transactional
    public LaboratorioResumo criar(String nome, Long criadorId) {
        String nomeNormalizado = nome.trim();
        Laboratorio laboratorio = new Laboratorio(uuidFactory.generate(), nomeNormalizado, criadorId);
        laboratorioRepository.save(laboratorio);

        Participacao participacao = new Participacao(
                uuidFactory.generate(), laboratorio, criadorId, clock.instant());
        participacaoRepository.save(participacao);
        papelRepository.saveAll(List.of(
                novoPapel(participacao, PapelLaboratorio.MEMBRO, criadorId),
                novoPapel(participacao, PapelLaboratorio.ADMIN, criadorId)));

        return new LaboratorioResumo(laboratorio.getId(), laboratorio.getNome(), laboratorio.getEstado(),
                Set.of(PapelLaboratorio.MEMBRO, PapelLaboratorio.ADMIN), laboratorio.getVersion());
    }

    @Transactional(readOnly = true)
    public List<LaboratorioResumo> listarMeus(Long usuarioId) {
        return participacaoRepository.findAllByUsuarioIdAndEstado(usuarioId, EstadoParticipacao.ATIVA)
                .stream()
                .map(participacao -> resumo(participacao.getLaboratorio(), participacao))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AdminLaboratorioResumo> listarParaAdmin(Specification<Laboratorio> filtros, Pageable pageable) {
        Page<Laboratorio> laboratorios = laboratorioRepository.findAll(filtros, pageable);
        if (laboratorios.isEmpty()) {
            return laboratorios.map(this::resumoAdminVazio);
        }

        List<UUID> laboratorioIds = laboratorios.getContent().stream().map(Laboratorio::getId).toList();
        Map<UUID, Long> membrosAtivosPorLaboratorio = new HashMap<>();
        participacaoRepository.contarAtivasPorLaboratorio(laboratorioIds)
                .forEach(contagem -> membrosAtivosPorLaboratorio.put(
                        contagem.getLaboratorioId(), contagem.getQuantidade()));

        Map<UUID, Set<Long>> administradoresPorLaboratorio = new HashMap<>();
        papelRepository.listarAdministradoresAtivosPorLaboratorio(laboratorioIds)
                .forEach(administrador -> administradoresPorLaboratorio
                        .computeIfAbsent(administrador.getLaboratorioId(), ignorado -> new LinkedHashSet<>())
                        .add(administrador.getUsuarioId()));

        List<Long> usuarioIds = administradoresPorLaboratorio.values().stream()
                .flatMap(Collection::stream)
                .distinct()
                .toList();
        Map<Long, UsuarioRosterInfo> usuariosPorId = usuarioRosterQuery.buscarPorIds(usuarioIds).stream()
                .collect(Collectors.toMap(UsuarioRosterInfo::usuarioId, Function.identity()));

        Comparator<AdminLaboratorioResumo.Administrador> ordemAdministradores =
                Comparator.comparing(AdminLaboratorioResumo.Administrador::nome, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(AdminLaboratorioResumo.Administrador::email, String.CASE_INSENSITIVE_ORDER);

        return laboratorios.map(laboratorio -> {
            List<AdminLaboratorioResumo.Administrador> administradores = administradoresPorLaboratorio
                    .getOrDefault(laboratorio.getId(), Set.of()).stream()
                    .map(usuarioId -> {
                        UsuarioRosterInfo usuario = usuariosPorId.get(usuarioId);
                        if (usuario == null) {
                            throw new IllegalStateException("Conta de administrador de laboratório não encontrada.");
                        }
                        return new AdminLaboratorioResumo.Administrador(usuario.nome(), usuario.email());
                    })
                    .sorted(ordemAdministradores)
                    .toList();
            return new AdminLaboratorioResumo(laboratorio.getId(), laboratorio.getNome(), laboratorio.getEstado(),
                    membrosAtivosPorLaboratorio.getOrDefault(laboratorio.getId(), 0L), administradores);
        });
    }

    @Transactional(readOnly = true)
    public LaboratorioResumo buscar(UUID laboratorioId, Long usuarioId) {
        Laboratorio laboratorio = accessPolicy.exigirAcesso(laboratorioId, usuarioId);
        Participacao participacao = accessPolicy.exigirParticipacaoAtiva(laboratorioId, usuarioId);
        return resumo(laboratorio, participacao);
    }

    @Transactional
    public LaboratorioResumo atualizarNome(UUID laboratorioId, Long usuarioId, String nome) {
        Laboratorio laboratorio = accessPolicy.bloquearLaboratorioParaAdmin(laboratorioId, usuarioId);
        laboratorio.atualizarNome(nome.trim());
        return resumo(laboratorio, accessPolicy.exigirParticipacaoAtiva(laboratorioId, usuarioId));
    }

    @Transactional(readOnly = true)
    public List<ParticipacaoResumo> listarParticipacoes(UUID laboratorioId, Long usuarioId) {
        accessPolicy.exigirAcesso(laboratorioId, usuarioId);
        // Decisão de produto: todo participante ativo vê nome, papéis e e-mail dos colegas.
        List<Participacao> participacoes = participacaoRepository.findAllByLaboratorioIdAndEstadoOrderByCreatedAtAsc(
                laboratorioId, EstadoParticipacao.ATIVA);
        Map<UUID, Set<PapelLaboratorio>> papeis = papeisPorParticipacao(participacoes);
        Collection<Long> usuarioIds = participacoes.stream().map(Participacao::getUsuarioId).toList();
        Map<Long, UsuarioRosterInfo> usuarios = usuarioRosterQuery.buscarPorIds(usuarioIds).stream()
                .collect(Collectors.toMap(UsuarioRosterInfo::usuarioId, Function.identity()));

        return participacoes.stream()
                .map(participacao -> {
                    UsuarioRosterInfo usuario = usuarios.get(participacao.getUsuarioId());
                    if (usuario == null) {
                        throw new IllegalStateException("Conta de participante não encontrada.");
                    }
                    return new ParticipacaoResumo(participacao.getId(), usuario.nome(),
                            usuario.email(),
                            papeis.getOrDefault(participacao.getId(), Set.of()), participacao.getVersion());
                })
                .toList();
    }

    @Transactional
    public ParticipacaoResumo alterarPapeis(UUID laboratorioId, UUID participacaoId, Long atorId,
                                             Long versaoEsperada, Set<PapelLaboratorio> papeisDesejados) {
        accessPolicy.bloquearLaboratorioParaAdmin(laboratorioId, atorId);
        Participacao alvo = participacaoRepository.findByIdAndLaboratorioIdForUpdate(participacaoId, laboratorioId)
                .filter(participacao -> participacao.getEstado() == EstadoParticipacao.ATIVA)
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
        if (!alvo.getVersion().equals(versaoEsperada)) {
            throw new ConflictException(CONFLITO_GENERICO);
        }
        if (!papeisDesejados.contains(PapelLaboratorio.MEMBRO)) {
            throw new ConflictException(CONFLITO_GENERICO);
        }

        Set<PapelLaboratorio> papeisAtuais = accessPolicy.papeisAtivos(alvo.getId());
        if (papeisAtuais.equals(papeisDesejados)) {
            return resumoParticipacao(alvo, papeisAtuais, versaoEsperada);
        }
        if (papeisAtuais.contains(PapelLaboratorio.ORIENTADOR)
                && !papeisDesejados.contains(PapelLaboratorio.ORIENTADOR)) {
            orientacaoLifecycleService.exigirSemVinculosAtivos(alvo.getId());
            orientacaoLifecycleService.encerrarDaParticipacao(laboratorioId, alvo.getId(), atorId, clock.instant());
        }
        if (papeisAtuais.contains(PapelLaboratorio.ADMIN)
                && !papeisDesejados.contains(PapelLaboratorio.ADMIN)
                && papelRepository.contarParticipacoesAtivasComPapel(laboratorioId, PapelLaboratorio.ADMIN) <= 1) {
            throw new ConflictException(CONFLITO_GENERICO);
        }

        Instant agora = clock.instant();
        alvo.registrarAlteracaoDePapeis(agora);
        List<ParticipacaoPapel> papeisAtivos = papelRepository
                .findAllByParticipacaoIdAndRevogadoEmIsNull(alvo.getId());
        Map<PapelLaboratorio, ParticipacaoPapel> atuais = papeisAtivos.stream()
                .collect(Collectors.toMap(ParticipacaoPapel::getPapel, Function.identity()));

        papeisAtivos.stream()
                .filter(papel -> !papeisDesejados.contains(papel.getPapel()))
                .forEach(papel -> papel.revogar(atorId, agora));
        papeisDesejados.stream()
                .filter(papel -> !atuais.containsKey(papel))
                .map(papel -> novoPapel(alvo, papel, atorId))
                .forEach(papelRepository::save);

        entityManager.flush();
        Set<PapelLaboratorio> papeisFinais = EnumSet.copyOf(papeisDesejados);
        return resumoParticipacao(alvo, papeisFinais, alvo.getVersion());
    }

    @Transactional
    public void sair(UUID laboratorioId, Long usuarioId) {
        accessPolicy.bloquearLaboratorio(laboratorioId, usuarioId);
        Participacao alvo = accessPolicy.exigirParticipacaoAtiva(laboratorioId, usuarioId);
        encerrarParticipacao(laboratorioId, alvo, usuarioId, EstadoParticipacao.SAIU);
    }

    @Transactional
    public void remover(UUID laboratorioId, UUID participacaoId, Long atorId) {
        accessPolicy.bloquearLaboratorioParaAdmin(laboratorioId, atorId);
        Participacao alvo = participacaoRepository.findByIdAndLaboratorioIdForUpdate(participacaoId, laboratorioId)
                .filter(participacao -> participacao.getEstado() == EstadoParticipacao.ATIVA)
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
        encerrarParticipacao(laboratorioId, alvo, atorId, EstadoParticipacao.REMOVIDA);
    }

    @Transactional
    public void desativar(UUID laboratorioId, Long operadorId) {
        Laboratorio laboratorio = laboratorioRepository.findByIdForUpdate(laboratorioId)
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
        if (laboratorio.getEstado() == EstadoLaboratorio.ATIVO) {
            laboratorio.desativar(operadorId, clock.instant());
            auditarAposCommit("desativado", laboratorioId, operadorId);
        }
    }

    @Transactional
    public void reativar(UUID laboratorioId, Long operadorId) {
        Laboratorio laboratorio = laboratorioRepository.findByIdForUpdate(laboratorioId)
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
        if (laboratorio.getEstado() == EstadoLaboratorio.DESATIVADO) {
            laboratorio.reativar();
            auditarAposCommit("reativado", laboratorioId, operadorId);
        }
    }

    private void auditarAposCommit(String acao, UUID laboratorioId, Long operadorId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Laboratório {} {} pelo usuário {}", laboratorioId, acao, operadorId);
            }
        });
    }

    private void encerrarParticipacao(UUID laboratorioId, Participacao alvo, Long atorId,
                                      EstadoParticipacao estadoFinal) {
        Set<PapelLaboratorio> papeisAtuais = accessPolicy.papeisAtivos(alvo.getId());
        if (papeisAtuais.contains(PapelLaboratorio.ADMIN)
                && papelRepository.contarParticipacoesAtivasComPapel(laboratorioId, PapelLaboratorio.ADMIN) <= 1) {
            throw new ConflictException(CONFLITO_GENERICO);
        }

        Instant agora = clock.instant();
        orientacaoLifecycleService.encerrarDaParticipacao(laboratorioId, alvo.getId(), atorId, agora);
        if (estadoFinal == EstadoParticipacao.SAIU) {
            alvo.sair(atorId, agora);
        } else {
            alvo.remover(atorId, agora);
        }
        papelRepository.findAllByParticipacaoIdAndRevogadoEmIsNull(alvo.getId())
                .forEach(papel -> papel.revogar(atorId, agora));
    }

    private ParticipacaoPapel novoPapel(Participacao participacao, PapelLaboratorio papel, Long atorId) {
        return new ParticipacaoPapel(uuidFactory.generate(), participacao, papel, atorId);
    }

    private LaboratorioResumo resumo(Laboratorio laboratorio, Participacao participacao) {
        return new LaboratorioResumo(laboratorio.getId(), laboratorio.getNome(), laboratorio.getEstado(),
                accessPolicy.papeisAtivos(participacao.getId()), laboratorio.getVersion());
    }

    private AdminLaboratorioResumo resumoAdminVazio(Laboratorio laboratorio) {
        return new AdminLaboratorioResumo(laboratorio.getId(), laboratorio.getNome(), laboratorio.getEstado(), 0,
                List.of());
    }

    private ParticipacaoResumo resumoParticipacao(Participacao participacao, Set<PapelLaboratorio> papeis,
                                                   Long versao) {
        UsuarioRosterInfo usuario = usuarioRosterQuery.buscarPorIds(List.of(participacao.getUsuarioId())).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Conta de participante não encontrada."));
        return new ParticipacaoResumo(participacao.getId(), usuario.nome(), usuario.email(), papeis, versao);
    }

    private Map<UUID, Set<PapelLaboratorio>> papeisPorParticipacao(List<Participacao> participacoes) {
        if (participacoes.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<PapelLaboratorio>> papeis = new HashMap<>();
        papelRepository.findAllByParticipacaoIdInAndRevogadoEmIsNull(
                        participacoes.stream().map(Participacao::getId).toList())
                .forEach(papel -> papeis.computeIfAbsent(papel.getParticipacao().getId(), ignored -> new HashSet<>())
                        .add(papel.getPapel()));
        return papeis;
    }
}
