package br.ufla.citogenetica.contagem.internal.application;

import java.time.DateTimeException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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
import br.ufla.citogenetica.contagem.internal.domain.RegistroContagem;
import br.ufla.citogenetica.contagem.internal.domain.StatusRegistro;
import br.ufla.citogenetica.contagem.internal.domain.TipoAnormalidade;
import br.ufla.citogenetica.contagem.internal.persistence.FaseProtocoloRepository;
import br.ufla.citogenetica.contagem.internal.persistence.LaminaRepository;
import br.ufla.citogenetica.contagem.internal.persistence.ParametroContagemRepository;
import br.ufla.citogenetica.contagem.internal.persistence.RegistroContagemRepository;
import br.ufla.citogenetica.contagem.internal.persistence.TipoAnormalidadeRepository;
import br.ufla.citogenetica.shared.web.error.BadRequestException;
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
 * Registros de contagem. Escrita igual à da lâmina (só o dono, com o laboratório ativo), regras em
 * {@link AcessoLamina}. A criação é idempotente pelo id do cliente (ADR 0003): a repetição é resolvida antes de
 * qualquer checagem de acesso, compara só o hash do pedido original e nunca ressuscita registro excluído.
 */
@Service
@RequiredArgsConstructor
public class RegistroContagemService {

    public static final int MAXIMO_TIPOS = 50;
    public static final int MAXIMO_FUSO = 64;

    private static final String ID_COM_OUTRO_CONTEUDO = "Este identificador já foi usado em outro registro.";
    private static final String FASE_OU_TIPO_INVALIDO =
            "Fase ou tipo de anormalidade inválido para o protocolo desta lâmina.";
    // Faixa que o PostgreSQL e o ISO-8601 representam sem ambiguidade; fora dela a hora é certamente um erro.
    private static final Instant HORA_MINIMA = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant HORA_MAXIMA = Instant.parse("9999-12-31T23:59:59Z");

    private final AcessoLamina acesso;
    private final LaminaRepository laminaRepository;
    private final RegistroContagemRepository registroRepository;
    private final FaseProtocoloRepository faseRepository;
    private final TipoAnormalidadeRepository tipoRepository;
    private final ParametroContagemRepository parametroRepository;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Cria o registro ou reconhece a repetição idempotente. A inserção usa {@code ON CONFLICT DO NOTHING}: quem
     * perde uma corrida pelo mesmo id relê a linha e responde repetição ou conflito, sem abortar a transação.
     * Antes de gravar, a linha da lâmina é travada ({@code PESSIMISTIC_WRITE}, sem aumentar a versão) e a
     * exclusão é revalidada: assim nenhum registro nasce em lâmina que acabou de ser excluída.
     */
    @Transactional
    public CriacaoRegistro criar(NovoRegistro novo, Long usuarioId) {
        StatusRegistro status = status(novo.status());
        List<UUID> tipoIds = tiposOrdenados(novo.tipoIds(), status);
        Instant horaAparelho = horaAparelho(novo.horaAparelho());
        String fusoAparelho = fusoAparelho(novo.fusoAparelho());
        int quantidade = novo.quantidade();
        String hash = HashConteudoRegistro.calcular(
                novo.laminaId(), novo.faseId(), status, tipoIds, quantidade, horaAparelho, fusoAparelho);

        var existente = registroRepository.findById(novo.id());
        if (existente.isPresent()) {
            return repeticao(existente.get(), hash, usuarioId);
        }

        Lamina lamina = acesso.carregarParaEscrita(novo.laminaId(), usuarioId);
        exigirQuantidadeDentroDoLimite(quantidade);
        validarFaseETipos(lamina, novo.faseId(), status, tipoIds);

        entityManager.refresh(lamina, LockModeType.PESSIMISTIC_WRITE);
        if (lamina.estaExcluida()) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }

        Instant agora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int inseridos = registroRepository.inserirSeAusente(novo.id(), agora, lamina.getId(), novo.faseId(),
                status.name(), quantidade, horaAparelho, fusoAparelho, hash);
        RegistroContagem gravado = registroRepository.findById(novo.id())
                .orElseThrow(() -> new ConflictException(ID_COM_OUTRO_CONTEUDO));
        if (inseridos == 1) {
            tipoIds.forEach(tipoId -> registroRepository.inserirTipo(gravado.getId(), tipoId));
            return new CriacaoRegistro(resumir(gravado, tipoIds), true);
        }
        // Outra requisição venceu a corrida pelo mesmo id.
        return repeticao(gravado, hash, usuarioId);
    }

    /** Id de outro dono é indistinguível de recurso desconhecido. A resposta é o estado atual do registro. */
    private CriacaoRegistro repeticao(RegistroContagem existente, String hash, Long usuarioId) {
        Lamina lamina = laminaRepository.findById(existente.getLaminaId())
                .orElseThrow(() -> new IllegalStateException("Lâmina do registro não encontrada."));
        if (!usuarioId.equals(lamina.getDonoUsuarioId())) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }
        if (!existente.getHashConteudoCriacao().equals(hash)) {
            throw new ConflictException(ID_COM_OUTRO_CONTEUDO);
        }
        return new CriacaoRegistro(resumir(existente, registroRepository.findTipoIds(existente.getId())), false);
    }

    private static RegistroResumo resumir(RegistroContagem registro, List<UUID> tipoIds) {
        return new RegistroResumo(registro.getId(), registro.getVersion(), registro.getLaminaId(),
                registro.getFaseId(), registro.getStatus(), tipoIds, registro.getQuantidade(),
                registro.getRegistradoEm(), registro.getHoraAparelho(), registro.getFusoAparelho());
    }

    private void exigirQuantidadeDentroDoLimite(int quantidade) {
        int limite = parametroRepository.findById(ParametroContagem.ID_UNICO)
                .orElseThrow(() -> new IllegalStateException("Parâmetros da contagem não encontrados."))
                .getLimiteCelulasPorRegistro();
        if (quantidade > limite) {
            throw new BadRequestException(
                    "A quantidade por registro deve ser de 1 a " + limite + " células.");
        }
    }

    /** Fase da versão de protocolo da lâmina e tipos dessa fase, fixos ou personalizados do dono no mesmo escopo. */
    private void validarFaseETipos(Lamina lamina, UUID faseId, StatusRegistro status, List<UUID> tipoIds) {
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

    private static StatusRegistro status(String bruto) {
        for (StatusRegistro status : StatusRegistro.values()) {
            if (status.name().equals(bruto)) {
                return status;
            }
        }
        throw new BadRequestException("A situação deve ser NORMAL ou ANORMAL.");
    }

    /** Normal não tem tipo; anormal tem ao menos um; sem repetição. Devolve ordenado pelo texto canônico. */
    private static List<UUID> tiposOrdenados(List<UUID> brutos, StatusRegistro status) {
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

    private static Instant horaAparelho(String bruta) {
        if (bruta == null) {
            return null;
        }
        Instant hora;
        try {
            hora = Instant.parse(bruta);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("A hora do aparelho deve ser um instante ISO-8601, como 2026-11-08T12:30:00Z.");
        }
        if (hora.isBefore(HORA_MINIMA) || hora.isAfter(HORA_MAXIMA)) {
            throw new BadRequestException("A hora do aparelho deve estar entre os anos 1970 e 9999.");
        }
        // O PostgreSQL guarda microssegundos; truncar aqui mantém o hash e a resposta iguais ao que está no banco.
        return hora.truncatedTo(ChronoUnit.MICROS);
    }

    private static String fusoAparelho(String bruto) {
        if (bruto == null) {
            return null;
        }
        if (bruto.codePointCount(0, bruto.length()) > MAXIMO_FUSO) {
            throw new BadRequestException("O fuso do aparelho deve ter no máximo " + MAXIMO_FUSO + " caracteres.");
        }
        try {
            ZoneId.of(bruto);
        } catch (DateTimeException e) {
            throw new BadRequestException(
                    "O fuso do aparelho deve ser um identificador IANA (America/Sao_Paulo) ou um deslocamento (-03:00).");
        }
        return bruto;
    }
}
