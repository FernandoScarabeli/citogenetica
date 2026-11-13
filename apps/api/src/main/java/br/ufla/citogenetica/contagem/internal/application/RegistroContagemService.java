package br.ufla.citogenetica.contagem.internal.application;

import java.time.DateTimeException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.Lamina;
import br.ufla.citogenetica.contagem.internal.domain.RegistroContagem;
import br.ufla.citogenetica.contagem.internal.domain.StatusRegistro;
import br.ufla.citogenetica.contagem.internal.persistence.LaminaRepository;
import br.ufla.citogenetica.contagem.internal.persistence.RegistroContagemRepository;
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

    public static final int MAXIMO_FUSO = 64;

    private static final String VERSAO_DESATUALIZADA =
            "O registro foi alterado depois da versão informada. Atualize os dados e tente novamente.";
    private static final String ID_COM_OUTRO_CONTEUDO = "Este identificador já foi usado em outro registro.";
    // Faixa que o PostgreSQL e o ISO-8601 representam sem ambiguidade; fora dela a hora é certamente um erro.
    private static final Instant HORA_MINIMA = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant HORA_MAXIMA = Instant.parse("9999-12-31T23:59:59Z");

    private final AcessoLamina acesso;
    private final LaminaRepository laminaRepository;
    private final RegistroContagemRepository registroRepository;
    private final ValidadorRegistro validador;
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
        StatusRegistro status = ValidadorRegistro.status(novo.status());
        List<UUID> tipoIds = ValidadorRegistro.tiposOrdenados(novo.tipoIds(), status);
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
        validador.exigirQuantidadeDentroDoLimite(quantidade);
        validador.validarFaseETipos(lamina, novo.faseId(), status, tipoIds);

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

    /**
     * Edição de um registro ativo da lâmina. Mesmas validações da criação; {@code registradoEm}, hora e fuso do
     * aparelho e o hash da criação não mudam. Toda edição que altere algo (inclusive só os tipos) aumenta a versão;
     * pedido idêntico ao estado atual não altera nada. A lâmina é travada antes de ler o registro: edições, criações
     * e desfazeres da mesma lâmina ficam em fila, então a checagem da versão é exata e a perdedora recebe 409.
     */
    @Transactional
    public RegistroResumo atualizar(EdicaoRegistro edicao, Long usuarioId) {
        StatusRegistro status = ValidadorRegistro.status(edicao.status());
        List<UUID> tipoIds = ValidadorRegistro.tiposOrdenados(edicao.tipoIds(), status);
        int quantidade = edicao.quantidade();

        Lamina lamina = acesso.carregarParaEscrita(edicao.laminaId(), usuarioId);
        validador.exigirQuantidadeDentroDoLimite(quantidade);
        validador.validarFaseETipos(lamina, edicao.faseId(), status, tipoIds);

        entityManager.refresh(lamina, LockModeType.PESSIMISTIC_WRITE);
        if (lamina.estaExcluida()) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }
        RegistroContagem registro = registroRepository.findById(edicao.registroId())
                .filter(candidato -> candidato.getLaminaId().equals(lamina.getId()) && !candidato.estaExcluido())
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
        if (!registro.getVersion().equals(edicao.versaoEsperada())) {
            throw new ConflictException(VERSAO_DESATUALIZADA);
        }

        boolean camposMudam = registro.mudaCampos(edicao.faseId(), status, quantidade);
        boolean tiposMudam = !registroRepository.findTipoIds(registro.getId()).equals(tipoIds);
        if (tiposMudam) {
            registroRepository.removerTipos(registro.getId());
            tipoIds.forEach(tipoId -> registroRepository.inserirTipo(registro.getId(), tipoId));
        }
        if (camposMudam) {
            registro.alterar(edicao.faseId(), status, quantidade);
            registroRepository.saveAndFlush(registro);
        } else if (tiposMudam) {
            // Só os tipos mudaram: a linha do registro não está suja, então a versão é aumentada explicitamente.
            registroRepository.aumentarVersao(registro.getId(), clock.instant().truncatedTo(ChronoUnit.MICROS));
            entityManager.refresh(registro);
        }
        return resumir(registro, tipoIds);
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

    static RegistroResumo resumir(RegistroContagem registro, List<UUID> tipoIds) {
        return new RegistroResumo(registro.getId(), registro.getVersion(), registro.getLaminaId(),
                registro.getFaseId(), registro.getStatus(), tipoIds, registro.getQuantidade(),
                registro.getRegistradoEm(), registro.getHoraAparelho(), registro.getFusoAparelho());
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
