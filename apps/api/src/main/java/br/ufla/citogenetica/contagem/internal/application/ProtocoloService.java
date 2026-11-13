package br.ufla.citogenetica.contagem.internal.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import br.ufla.citogenetica.contagem.internal.domain.FaseProtocolo;
import br.ufla.citogenetica.contagem.internal.domain.Protocolo;
import br.ufla.citogenetica.contagem.internal.domain.TipoAnormalidade;
import br.ufla.citogenetica.contagem.internal.domain.VersaoProtocolo;
import br.ufla.citogenetica.contagem.internal.persistence.FaseProtocoloRepository;
import br.ufla.citogenetica.contagem.internal.persistence.ProtocoloRepository;
import br.ufla.citogenetica.contagem.internal.persistence.TipoAnormalidadeRepository;
import br.ufla.citogenetica.contagem.internal.persistence.VersaoProtocoloRepository;
import br.ufla.citogenetica.shared.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de protocolos e de suas versões publicadas. Protocolos são alterados só por migration. */
@Service
@RequiredArgsConstructor
public class ProtocoloService {

    public static final String NAO_ENCONTRADO = "Recurso não encontrado.";

    private final ProtocoloRepository protocoloRepository;
    private final VersaoProtocoloRepository versaoRepository;
    private final FaseProtocoloRepository faseRepository;
    private final TipoAnormalidadeRepository tipoRepository;

    /** Cada protocolo com a versão publicada mais recente; protocolos sem versão publicada não aparecem. */
    @Transactional(readOnly = true)
    public List<ProtocoloResumo> listar() {
        return protocoloRepository.findAllByOrderByNomeAsc().stream()
                .map(protocolo -> versaoRepository
                        .findFirstByProtocoloIdAndPublicadaEmIsNotNullOrderByNumeroDesc(protocolo.getId())
                        .map(versao -> resumo(protocolo, versao)))
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional(readOnly = true)
    public VersaoProtocoloResumo buscarVersaoPublicada(UUID versaoId) {
        return versaoRepository.findByIdAndPublicadaEmIsNotNull(versaoId)
                .map(this::resumo)
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
    }

    private ProtocoloResumo resumo(Protocolo protocolo, VersaoProtocolo versao) {
        return new ProtocoloResumo(protocolo.getId(), protocolo.getCodigo(), protocolo.getNome(), resumo(versao));
    }

    private VersaoProtocoloResumo resumo(VersaoProtocolo versao) {
        Map<UUID, List<VersaoProtocoloResumo.TipoFixo>> tiposPorFase = new HashMap<>();
        for (TipoAnormalidade tipo : tipoRepository.findFixosDaVersao(versao.getId())) {
            tiposPorFase.computeIfAbsent(tipo.getFaseId(), id -> new ArrayList<>())
                    .add(new VersaoProtocoloResumo.TipoFixo(tipo.getId(), tipo.getNome()));
        }
        List<VersaoProtocoloResumo.Fase> fases = faseRepository.findByVersaoIdOrderByOrdemAsc(versao.getId()).stream()
                .map(fase -> resumo(fase, tiposPorFase.getOrDefault(fase.getId(), List.of())))
                .collect(Collectors.toList());
        return new VersaoProtocoloResumo(versao.getId(), versao.getNumero(), fases);
    }

    private static VersaoProtocoloResumo.Fase resumo(FaseProtocolo fase, List<VersaoProtocoloResumo.TipoFixo> tipos) {
        return new VersaoProtocoloResumo.Fase(
                fase.getId(), fase.getNome(), fase.getOrdem(), fase.isContaComoDivisao(), tipos);
    }
}
