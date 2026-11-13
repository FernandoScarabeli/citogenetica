package br.ufla.citogenetica.laboratorio.internal.application;

import java.util.Set;
import java.util.UUID;
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
import br.ufla.citogenetica.shared.web.error.ConflictException;
import br.ufla.citogenetica.shared.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Política local: o papel global da plataforma nunca concede participação no laboratório. */
@Service
@RequiredArgsConstructor
public class LaboratorioAccessPolicy {

    private static final String NAO_ENCONTRADO = "Laboratório não encontrado.";
    private static final String CONFLITO_GENERICO = "A operação não pode ser concluída.";

    private final LaboratorioRepository laboratorioRepository;
    private final ParticipacaoRepository participacaoRepository;
    private final ParticipacaoPapelRepository participacaoPapelRepository;

    public Laboratorio exigirAcesso(UUID laboratorioId, Long usuarioId) {
        Laboratorio laboratorio = laboratorioRepository.findById(laboratorioId)
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
        exigirParticipacaoAtiva(laboratorioId, usuarioId);
        return laboratorio;
    }

    /** Bloqueia sempre primeiro o laboratório, serializando qualquer mutação do seu estado. */
    public Laboratorio bloquearLaboratorio(UUID laboratorioId, Long usuarioId) {
        Laboratorio laboratorio = laboratorioRepository.findByIdForUpdate(laboratorioId)
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
        exigirParticipacaoAtiva(laboratorioId, usuarioId);
        if (laboratorio.getEstado() != EstadoLaboratorio.ATIVO) {
            throw new ConflictException(CONFLITO_GENERICO);
        }
        return laboratorio;
    }

    /** Obtém o lock do laboratório sem exigir que ele esteja ativo; o chamador decide como tratar o estado. */
    public Laboratorio bloquearLaboratorio(UUID laboratorioId) {
        return laboratorioRepository.findByIdForUpdate(laboratorioId)
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
    }

    public Laboratorio bloquearLaboratorioParaAdmin(UUID laboratorioId, Long usuarioId) {
        Laboratorio laboratorio = bloquearLaboratorio(laboratorioId, usuarioId);
        exigirAdmin(laboratorioId, usuarioId);
        return laboratorio;
    }

    /** Revogar convite continua permitido em laboratório desativado, pois apenas invalida acesso futuro. */
    public Laboratorio bloquearLaboratorioParaAdminSemExigirAtivo(UUID laboratorioId, Long usuarioId) {
        Laboratorio laboratorio = bloquearLaboratorio(laboratorioId);
        exigirAdmin(laboratorioId, usuarioId);
        return laboratorio;
    }

    public Participacao exigirParticipacaoAtiva(UUID laboratorioId, Long usuarioId) {
        return participacaoRepository.findByLaboratorioIdAndUsuarioIdAndEstado(
                        laboratorioId, usuarioId, EstadoParticipacao.ATIVA)
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
    }

    public Participacao exigirParticipacaoAtiva(UUID laboratorioId, UUID participacaoId) {
        return participacaoRepository.findByIdAndLaboratorioIdAndEstado(
                        participacaoId, laboratorioId, EstadoParticipacao.ATIVA)
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
    }

    public void exigirAdmin(UUID laboratorioId, Long usuarioId) {
        Participacao participacao = exigirParticipacaoAtiva(laboratorioId, usuarioId);
        if (!papeisAtivos(participacao.getId()).contains(PapelLaboratorio.ADMIN)) {
            throw new AccessDeniedException("Acesso negado.");
        }
    }

    public Set<PapelLaboratorio> papeisAtivos(UUID participacaoId) {
        return participacaoPapelRepository.findAllByParticipacaoIdAndRevogadoEmIsNull(participacaoId)
                .stream()
                .map(ParticipacaoPapel::getPapel)
                .collect(Collectors.toUnmodifiableSet());
    }

}
