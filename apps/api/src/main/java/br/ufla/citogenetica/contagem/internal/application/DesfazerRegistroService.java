package br.ufla.citogenetica.contagem.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.ufla.citogenetica.contagem.internal.domain.Lamina;
import br.ufla.citogenetica.contagem.internal.domain.OperacaoDesfazer;
import br.ufla.citogenetica.contagem.internal.domain.RegistroContagem;
import br.ufla.citogenetica.contagem.internal.domain.StatusRegistro;
import br.ufla.citogenetica.contagem.internal.persistence.LaminaRepository;
import br.ufla.citogenetica.contagem.internal.persistence.OperacaoDesfazerRepository;
import br.ufla.citogenetica.contagem.internal.persistence.RegistroContagemRepository;
import br.ufla.citogenetica.shared.web.error.ConflictException;
import br.ufla.citogenetica.shared.web.error.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static br.ufla.citogenetica.contagem.internal.application.ProtocoloService.NAO_ENCONTRADO;

/**
 * Desfazer as últimas células de uma fase, situação e conjunto exato de tipos, respeitando lotes (comportamento de
 * {@code removeMatching} do front). Escrita igual à da lâmina ({@link AcessoLamina}). É idempotente pelo id do
 * cliente (ADR 0003): a repetição é resolvida antes de qualquer checagem de acesso e devolve a resposta original,
 * guardada na operação, mesmo que os registros tenham mudado depois.
 */
@Service
@RequiredArgsConstructor
public class DesfazerRegistroService {

    private static final String ID_COM_OUTRO_CONTEUDO = "Este identificador já foi usado em outro desfazer.";

    private final AcessoLamina acesso;
    private final LaminaRepository laminaRepository;
    private final RegistroContagemRepository registroRepository;
    private final OperacaoDesfazerRepository operacaoRepository;
    private final ValidadorRegistro validador;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Tudo numa transação, com a lâmina travada ({@code PESSIMISTIC_WRITE}, sem aumentar a versão) antes de ler os
     * registros: desfazeres, edições e criações da mesma lâmina ficam em fila, então nunca se remove mais que o
     * disponível. A operação é gravada com {@code ON CONFLICT DO NOTHING} antes de mexer nos registros; quem perde
     * uma corrida pelo mesmo id responde repetição ou conflito sem remover nada. Se não há células suficientes, o
     * erro desfaz a transação inteira, inclusive a gravação da operação.
     */
    @Transactional
    public ResultadoDesfazer desfazer(PedidoDesfazer pedido, Long usuarioId) {
        StatusRegistro status = ValidadorRegistro.status(pedido.status());
        List<UUID> tipoIds = ValidadorRegistro.tiposOrdenados(pedido.tipoIds(), status);
        int quantidade = pedido.quantidade();
        String hash = HashConteudoRegistro.calcularDesfazer(
                pedido.laminaId(), pedido.faseId(), status, tipoIds, quantidade);

        var existente = operacaoRepository.findById(pedido.id());
        if (existente.isPresent()) {
            return repeticao(existente.get(), hash, usuarioId);
        }

        Lamina lamina = acesso.carregarParaEscrita(pedido.laminaId(), usuarioId);
        validador.validarFaseETipos(lamina, pedido.faseId(), status, tipoIds);

        entityManager.refresh(lamina, LockModeType.PESSIMISTIC_WRITE);
        if (lamina.estaExcluida()) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }

        Instant agora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int inseridos = operacaoRepository.inserirSeAusente(pedido.id(), agora, lamina.getId(), hash, quantidade);
        if (inseridos == 0) {
            // Outra requisição venceu a corrida pelo mesmo id.
            OperacaoDesfazer vencedora = operacaoRepository.findById(pedido.id())
                    .orElseThrow(() -> new ConflictException(ID_COM_OUTRO_CONTEUDO));
            return repeticao(vencedora, hash, usuarioId);
        }

        List<UUID> ids = registroRepository.findIdsParaDesfazer(lamina.getId(), pedido.faseId(), status.name(),
                tipoIds.stream().map(UUID::toString).collect(Collectors.joining(",")), quantidade);
        Map<UUID, RegistroContagem> registros = registroRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(RegistroContagem::getId, Function.identity()));
        long disponivel = registros.values().stream().mapToLong(RegistroContagem::getQuantidade).sum();
        if (disponivel < quantidade) {
            throw new ConflictException("Não há células suficientes para desfazer: foram pedidas " + quantidade
                    + " e há " + disponivel + " nessa fase, situação e combinação de tipos.");
        }

        List<RegistroDesfeito> afetados = new ArrayList<>();
        int faltante = quantidade;
        for (UUID id : ids) {
            RegistroContagem registro = registros.get(id);
            if (registro.getQuantidade() <= faltante) {
                faltante -= registro.getQuantidade();
                registro.excluir(agora);
                afetados.add(new RegistroDesfeito(id, 0, true));
            } else {
                registro.reduzirQuantidade(faltante);
                faltante = 0;
                afetados.add(new RegistroDesfeito(id, registro.getQuantidade(), false));
            }
            if (faltante == 0) {
                break;
            }
        }
        registroRepository.saveAllAndFlush(registros.values());
        for (int ordem = 0; ordem < afetados.size(); ordem++) {
            RegistroDesfeito afetado = afetados.get(ordem);
            operacaoRepository.inserirRegistroAfetado(
                    pedido.id(), ordem, afetado.id(), afetado.quantidadeRestante(), afetado.excluido());
        }
        return new ResultadoDesfazer(pedido.id(), quantidade, afetados);
    }

    /** Id de outro dono é indistinguível de desconhecido. A resposta é a original, não o estado atual. */
    private ResultadoDesfazer repeticao(OperacaoDesfazer operacao, String hash, Long usuarioId) {
        Lamina lamina = laminaRepository.findById(operacao.getLaminaId())
                .orElseThrow(() -> new IllegalStateException("Lâmina da operação não encontrada."));
        if (!usuarioId.equals(lamina.getDonoUsuarioId())) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }
        if (!operacao.getHashConteudo().equals(hash)) {
            throw new ConflictException(ID_COM_OUTRO_CONTEUDO);
        }
        List<RegistroDesfeito> registros = operacaoRepository.findRegistrosAfetados(operacao.getId()).stream()
                .map(afetado -> new RegistroDesfeito(
                        afetado.getRegistroId(), afetado.getQuantidadeRestante(), afetado.getExcluido()))
                .toList();
        return new ResultadoDesfazer(operacao.getId(), operacao.getQuantidadeDesfeita(), registros);
    }
}
