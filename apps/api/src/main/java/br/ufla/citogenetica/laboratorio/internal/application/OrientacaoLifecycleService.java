package br.ufla.citogenetica.laboratorio.internal.application;

import java.time.Instant;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.EstadoConviteOrientacao;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoVinculoOrientacao;
import br.ufla.citogenetica.laboratorio.internal.persistence.ConviteOrientacaoRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.VinculoOrientacaoRepository;
import br.ufla.citogenetica.shared.web.error.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Regras de orientação que acompanham, na mesma transação, o ciclo de vida da participação. */
@Service
@RequiredArgsConstructor
public class OrientacaoLifecycleService {

    private final VinculoOrientacaoRepository vinculoRepository;
    private final ConviteOrientacaoRepository conviteRepository;

    public void exigirSemVinculosAtivos(UUID participacaoId) {
        if (vinculoRepository.existsVinculoAtivoDaParticipacao(participacaoId, EstadoVinculoOrientacao.ATIVO)) {
            throw new ConflictException(
                    "Encerre os vínculos ativos de orientação antes de retirar o papel ORIENTADOR.");
        }
    }

    /** Remover alguém que ainda orienta pessoas deixaria os orientandos sem orientador: antes encerra-se a orientação. */
    public void exigirSemOrientandosAtivos(UUID participacaoId, String nome) {
        if (vinculoRepository.existsByOrientadorParticipacaoIdAndEstado(participacaoId, EstadoVinculoOrientacao.ATIVO)) {
            throw new ConflictException("Encerre as orientações de " + nome + " antes de removê-lo do laboratório.");
        }
    }

    public void encerrarDaParticipacao(UUID laboratorioId, UUID participacaoId, Long operadorId, Instant instante) {
        vinculoRepository.findAllByParticipacaoForUpdate(
                        laboratorioId, participacaoId, EstadoVinculoOrientacao.ATIVO)
                .forEach(vinculo -> vinculo.encerrar(operadorId, instante));
        conviteRepository.findAllByLaboratorioIdAndOrientadorParticipacaoIdAndEstadoOrderByCreatedAtDesc(
                        laboratorioId, participacaoId, EstadoConviteOrientacao.PENDENTE)
                .forEach(convite -> convite.revogar(operadorId, instante));
    }
}
