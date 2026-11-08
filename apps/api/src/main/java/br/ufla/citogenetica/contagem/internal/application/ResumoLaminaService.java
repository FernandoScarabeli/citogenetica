package br.ufla.citogenetica.contagem.internal.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.Lamina;
import br.ufla.citogenetica.contagem.internal.persistence.RegistroContagemRepository;
import br.ufla.citogenetica.contagem.internal.persistence.RegistroContagemRepository.TotalFase;
import br.ufla.citogenetica.contagem.internal.persistence.RegistroContagemRepository.TotalTipo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cálculos oficiais do servidor, por agregação SQL (sem carregar registros): sempre somam {@code quantidade} e
 * ignoram registros excluídos. Mesma conta de {@code summary()} em {@code apps/web/src/domain.ts}. Leitura igual
 * à da lâmina, via {@link AcessoLamina}.
 */
@Service
@RequiredArgsConstructor
public class ResumoLaminaService {

    private final AcessoLamina acesso;
    private final RegistroContagemRepository registroRepository;

    /** REPEATABLE_READ para que as duas agregações (fases e tipos) enxerguem o mesmo instante. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResumoLamina resumir(UUID laminaId, Long usuarioId) {
        Lamina lamina = acesso.carregarParaLeitura(laminaId, usuarioId);

        Map<UUID, List<TotalTipo>> tiposPorFase = new HashMap<>();
        for (TotalTipo tipo : registroRepository.totaisPorTipo(lamina.getId())) {
            tiposPorFase.computeIfAbsent(tipo.getFaseId(), fase -> new ArrayList<>()).add(tipo);
        }

        List<ResumoLamina.Fase> fases = new ArrayList<>();
        long total = 0;
        long anormais = 0;
        long divisao = 0;
        for (TotalFase linha : registroRepository.totaisPorFase(lamina.getId(), lamina.getProtocoloVersaoId())) {
            long totalFase = linha.getNormais() + linha.getAnormais();
            List<ResumoLamina.Tipo> tipos = tiposPorFase.getOrDefault(linha.getFaseId(), List.of()).stream()
                    .map(tipo -> new ResumoLamina.Tipo(tipo.getTipoId(), tipo.getNome(), tipo.getContagem(),
                            percentual(tipo.getContagem(), totalFase)))
                    .toList();
            fases.add(new ResumoLamina.Fase(linha.getFaseId(), linha.getNome(), linha.getOrdem(), linha.getNormais(),
                    linha.getAnormais(), totalFase, percentual(linha.getAnormais(), totalFase), tipos));
            total += totalFase;
            anormais += linha.getAnormais();
            if (Boolean.TRUE.equals(linha.getContaComoDivisao())) {
                divisao += totalFase;
            }
        }
        return new ResumoLamina(lamina.getId(), total, anormais, percentual(divisao, total), List.copyOf(fases));
    }

    /** Parte ÷ total × 100, na mesma ordem de operações do front; total zero dá 0. */
    private static double percentual(long parte, long total) {
        return total == 0 ? 0 : (double) parte / total * 100;
    }
}
