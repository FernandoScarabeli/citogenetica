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

    /** Lâminas por consulta em lote; folgado em relação ao limite de parâmetros do driver. */
    private static final int TAMANHO_BLOCO = 500;

    private final AcessoLamina acesso;
    private final RegistroContagemRepository registroRepository;

    /** REPEATABLE_READ para que as duas agregações (fases e tipos) enxerguem o mesmo instante. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResumoLamina resumir(UUID laminaId, Long usuarioId) {
        Lamina lamina = acesso.carregarParaLeitura(laminaId, usuarioId);
        return calcular(lamina.getId(),
                registroRepository.totaisPorFase(lamina.getId(), lamina.getProtocoloVersaoId()),
                registroRepository.totaisPorTipo(lamina.getId()));
    }

    /**
     * Mesmos cálculos de {@link #resumir} para várias lâminas já autorizadas, com a agregação em lote (duas consultas
     * por bloco, sem N+1). Deve rodar dentro de uma transação do chamador, para enxergar o mesmo instante dos demais
     * dados.
     */
    public Map<UUID, ResumoLamina> resumirEmLote(List<UUID> laminaIds) {
        Map<UUID, ResumoLamina> resultado = new HashMap<>();
        for (int inicio = 0; inicio < laminaIds.size(); inicio += TAMANHO_BLOCO) {
            List<UUID> bloco = laminaIds.subList(inicio, Math.min(inicio + TAMANHO_BLOCO, laminaIds.size()));
            Map<UUID, List<TotalFase>> fases = new HashMap<>();
            for (var linha : registroRepository.totaisPorFaseDasLaminas(bloco)) {
                fases.computeIfAbsent(linha.getLaminaId(), id -> new ArrayList<>()).add(linha);
            }
            Map<UUID, List<TotalTipo>> tipos = new HashMap<>();
            for (var linha : registroRepository.totaisPorTipoDasLaminas(bloco)) {
                tipos.computeIfAbsent(linha.getLaminaId(), id -> new ArrayList<>()).add(linha);
            }
            for (UUID id : bloco) {
                resultado.put(id, calcular(id, fases.getOrDefault(id, List.of()), tipos.getOrDefault(id, List.of())));
            }
        }
        return resultado;
    }

    /** Conta oficial a partir das linhas agregadas (fases na ordem do protocolo; tipos já ordenados por fase). */
    private static ResumoLamina calcular(UUID laminaId, List<? extends TotalFase> linhasFase,
                                         List<? extends TotalTipo> linhasTipo) {
        Map<UUID, List<TotalTipo>> tiposPorFase = new HashMap<>();
        for (TotalTipo tipo : linhasTipo) {
            tiposPorFase.computeIfAbsent(tipo.getFaseId(), fase -> new ArrayList<>()).add(tipo);
        }

        List<ResumoLamina.Fase> fases = new ArrayList<>();
        long total = 0;
        long anormais = 0;
        long divisao = 0;
        for (TotalFase linha : linhasFase) {
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
        return new ResumoLamina(laminaId, total, anormais, percentual(divisao, total), List.copyOf(fases));
    }

    /** Parte ÷ total × 100, na mesma ordem de operações do front; total zero dá 0. */
    private static double percentual(long parte, long total) {
        return total == 0 ? 0 : (double) parte / total * 100;
    }
}
