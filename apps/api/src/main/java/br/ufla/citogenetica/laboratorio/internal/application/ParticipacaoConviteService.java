package br.ufla.citogenetica.laboratorio.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import br.ufla.citogenetica.laboratorio.internal.domain.EstadoParticipacao;
import br.ufla.citogenetica.laboratorio.internal.domain.Laboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.Participacao;
import br.ufla.citogenetica.laboratorio.internal.domain.ParticipacaoPapel;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoPapelRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoRepository;
import br.ufla.citogenetica.shared.persistence.UuidFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reutilizável por diferentes fluxos de aceite que adicionam alguém ao laboratório. */
@Service
@RequiredArgsConstructor
public class ParticipacaoConviteService {

    private final ParticipacaoRepository participacaoRepository;
    private final ParticipacaoPapelRepository papelRepository;
    private final UuidFactory uuidFactory;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Participacao garantirParticipacaoAtiva(Laboratorio laboratorio, Long usuarioId,
                                                   Long concedidoPorUsuarioId,
                                                   Set<PapelLaboratorio> papeisConcedidos) {
        EnumSet<PapelLaboratorio> desejados = EnumSet.of(PapelLaboratorio.MEMBRO);
        desejados.addAll(papeisConcedidos);

        var existente = participacaoRepository
                .findByLaboratorioIdAndUsuarioIdAndEstadoForUpdate(
                        laboratorio.getId(), usuarioId, EstadoParticipacao.ATIVA);
        if (existente.isEmpty()) {
            Participacao nova = new Participacao(uuidFactory.generate(), laboratorio, usuarioId, clock.instant());
            participacaoRepository.save(nova);
            List<ParticipacaoPapel> papeis = desejados.stream()
                    .map(papel -> novoPapel(nova, papel, concedidoPorUsuarioId))
                    .toList();
            papelRepository.saveAll(papeis);
            entityManager.flush();
            return nova;
        }
        Participacao participacao = existente.orElseThrow();

        List<ParticipacaoPapel> atuais = papelRepository
                .findAllByParticipacaoIdAndRevogadoEmIsNull(participacao.getId());
        Set<PapelLaboratorio> papeisAtuais = EnumSet.noneOf(PapelLaboratorio.class);
        atuais.forEach(papel -> papeisAtuais.add(papel.getPapel()));
        if (papeisAtuais.containsAll(desejados)) {
            return participacao;
        }

        Instant agora = clock.instant();
        participacao.registrarAlteracaoDePapeis(agora);
        List<ParticipacaoPapel> novosPapeis = new ArrayList<>();
        desejados.stream()
                .filter(papel -> !papeisAtuais.contains(papel))
                .map(papel -> novoPapel(participacao, papel, concedidoPorUsuarioId))
                .forEach(novosPapeis::add);
        papelRepository.saveAll(novosPapeis);
        entityManager.flush();
        return participacao;
    }

    private ParticipacaoPapel novoPapel(Participacao participacao, PapelLaboratorio papel, Long atorId) {
        return new ParticipacaoPapel(uuidFactory.generate(), participacao, papel, atorId);
    }
}
