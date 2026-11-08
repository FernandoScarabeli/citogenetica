package br.ufla.citogenetica.contagem.internal.application;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.ufla.citogenetica.contagem.internal.domain.FaseProtocolo;
import br.ufla.citogenetica.contagem.internal.domain.Lamina;
import br.ufla.citogenetica.contagem.internal.domain.ParametroContagem;
import br.ufla.citogenetica.contagem.internal.domain.StatusRegistro;
import br.ufla.citogenetica.contagem.internal.domain.TipoAnormalidade;
import br.ufla.citogenetica.contagem.internal.persistence.FaseProtocoloRepository;
import br.ufla.citogenetica.contagem.internal.persistence.ParametroContagemRepository;
import br.ufla.citogenetica.contagem.internal.persistence.TipoAnormalidadeRepository;
import br.ufla.citogenetica.shared.web.error.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validações de fase, situação, tipos e quantidade compartilhadas por criação, edição e desfazer de registros,
 * para que as três rotas recusem exatamente as mesmas entradas com as mesmas mensagens.
 */
@Component
@RequiredArgsConstructor
class ValidadorRegistro {

    static final int MAXIMO_TIPOS = 50;

    private static final String FASE_OU_TIPO_INVALIDO =
            "Fase ou tipo de anormalidade inválido para o protocolo desta lâmina.";

    private final FaseProtocoloRepository faseRepository;
    private final TipoAnormalidadeRepository tipoRepository;
    private final ParametroContagemRepository parametroRepository;

    void exigirQuantidadeDentroDoLimite(int quantidade) {
        int limite = parametroRepository.findById(ParametroContagem.ID_UNICO)
                .orElseThrow(() -> new IllegalStateException("Parâmetros da contagem não encontrados."))
                .getLimiteCelulasPorRegistro();
        if (quantidade > limite) {
            throw new BadRequestException(
                    "A quantidade por registro deve ser de 1 a " + limite + " células.");
        }
    }

    /** Fase da versão de protocolo da lâmina e tipos dessa fase, fixos ou personalizados do dono no mesmo escopo. */
    void validarFaseETipos(Lamina lamina, UUID faseId, StatusRegistro status, List<UUID> tipoIds) {
        FaseProtocolo fase = faseRepository.findById(faseId)
                .filter(candidata -> candidata.getVersaoId().equals(lamina.getProtocoloVersaoId()))
                .orElseThrow(() -> new BadRequestException(FASE_OU_TIPO_INVALIDO));
        if (status == StatusRegistro.NORMAL) {
            return;
        }
        Map<UUID, TipoAnormalidade> encontrados = tipoRepository.findAllById(tipoIds).stream()
                .collect(Collectors.toMap(TipoAnormalidade::getId, Function.identity()));
        for (UUID tipoId : tipoIds) {
            TipoAnormalidade tipo = encontrados.get(tipoId);
            if (tipo == null || !tipoPodeSerUsado(tipo, fase, lamina)) {
                throw new BadRequestException(FASE_OU_TIPO_INVALIDO);
            }
        }
    }

    private static boolean tipoPodeSerUsado(TipoAnormalidade tipo, FaseProtocolo fase, Lamina lamina) {
        if (!tipo.getFaseId().equals(fase.getId())) {
            return false;
        }
        if (tipo.getUsuarioId() == null) {
            return true;
        }
        return tipo.getUsuarioId().equals(lamina.getDonoUsuarioId())
                && Objects.equals(tipo.getLaboratorioId(), lamina.getLaboratorioId());
    }

    static StatusRegistro status(String bruto) {
        for (StatusRegistro status : StatusRegistro.values()) {
            if (status.name().equals(bruto)) {
                return status;
            }
        }
        throw new BadRequestException("A situação deve ser NORMAL ou ANORMAL.");
    }

    /** Normal não tem tipo; anormal tem ao menos um; sem repetição. Devolve ordenado pelo texto canônico. */
    static List<UUID> tiposOrdenados(List<UUID> brutos, StatusRegistro status) {
        List<UUID> tipos = brutos == null ? List.of() : brutos;
        if (tipos.size() > MAXIMO_TIPOS) {
            throw new BadRequestException("Informe no máximo " + MAXIMO_TIPOS + " tipos de anormalidade.");
        }
        Set<UUID> distintos = new HashSet<>(tipos);
        if (distintos.size() != tipos.size()) {
            throw new BadRequestException("Os tipos de anormalidade não podem se repetir.");
        }
        if (status == StatusRegistro.NORMAL && !tipos.isEmpty()) {
            throw new BadRequestException("Um registro normal não pode ter tipos de anormalidade.");
        }
        if (status == StatusRegistro.ANORMAL && tipos.isEmpty()) {
            throw new BadRequestException("Um registro anormal precisa de ao menos um tipo de anormalidade.");
        }
        return tipos.stream().sorted(Comparator.comparing(UUID::toString)).toList();
    }
}
