package br.ufla.citogenetica.laboratorio.internal.application;

import java.util.UUID;

import br.ufla.citogenetica.laboratorio.api.LaboratorioAccessQuery;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoParticipacao;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoVinculoOrientacao;
import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.persistence.LaboratorioRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoPapelRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.VinculoOrientacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LaboratorioAccessQueryService implements LaboratorioAccessQuery {

    private final LaboratorioRepository laboratorioRepository;
    private final ParticipacaoRepository participacaoRepository;
    private final ParticipacaoPapelRepository participacaoPapelRepository;
    private final VinculoOrientacaoRepository vinculoRepository;

    /**
     * Leitura: o próprio pesquisador, seus orientadores com vínculo ativo e os admins do laboratório.
     * O leitor precisa ser participante ativo. Dados de quem saiu continuam visíveis aos admins
     * (os dados ficam no laboratório); o próprio ex-participante e os ex-orientadores perdem o acesso.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean podeVerDadosDoPesquisador(UUID laboratorioId, Long pesquisadorUsuarioId, Long leitorUsuarioId) {
        if (laboratorioId == null || pesquisadorUsuarioId == null || leitorUsuarioId == null) {
            return false;
        }
        var leitor = participacaoRepository.findByLaboratorioIdAndUsuarioIdAndEstado(
                laboratorioId, leitorUsuarioId, EstadoParticipacao.ATIVA);
        if (leitor.isEmpty()) {
            return false;
        }
        if (pesquisadorUsuarioId.equals(leitorUsuarioId)) {
            return true;
        }
        if (participacaoPapelRepository.existsByParticipacaoIdAndPapelAndRevogadoEmIsNull(
                leitor.get().getId(), PapelLaboratorio.ADMIN)) {
            return participacaoRepository.existsByLaboratorioIdAndUsuarioId(laboratorioId, pesquisadorUsuarioId);
        }
        return vinculoRepository.existsByLaboratorioIdAndOrientadorUsuarioIdAndPesquisadorUsuarioIdAndEstado(
                laboratorioId, leitorUsuarioId, pesquisadorUsuarioId, EstadoVinculoOrientacao.ATIVO);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean podeEditarDadosDoPesquisador(UUID laboratorioId, Long pesquisadorUsuarioId, Long editorUsuarioId) {
        if (laboratorioId == null || pesquisadorUsuarioId == null || editorUsuarioId == null
                || !pesquisadorUsuarioId.equals(editorUsuarioId)) {
            return false;
        }
        return laboratorioRepository.findById(laboratorioId)
                .filter(laboratorio -> laboratorio.getEstado() == EstadoLaboratorio.ATIVO)
                .flatMap(laboratorio -> participacaoRepository.findByLaboratorioIdAndUsuarioIdAndEstado(
                        laboratorioId, pesquisadorUsuarioId, EstadoParticipacao.ATIVA))
                .isPresent();
    }
}
