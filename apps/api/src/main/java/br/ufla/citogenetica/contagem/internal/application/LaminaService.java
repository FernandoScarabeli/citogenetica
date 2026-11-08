package br.ufla.citogenetica.contagem.internal.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import java.time.Clock;

import br.ufla.citogenetica.contagem.internal.domain.Lamina;
import br.ufla.citogenetica.contagem.internal.persistence.LaminaRepository;
import br.ufla.citogenetica.contagem.internal.persistence.VersaoProtocoloRepository;
import br.ufla.citogenetica.laboratorio.api.LaboratorioAccessQuery;
import br.ufla.citogenetica.shared.web.error.ConflictException;
import br.ufla.citogenetica.shared.web.error.NotFoundException;
import br.ufla.citogenetica.usuario.api.UsuarioRosterInfo;
import br.ufla.citogenetica.usuario.api.UsuarioRosterQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static br.ufla.citogenetica.contagem.internal.application.ProtocoloService.NAO_ENCONTRADO;

/**
 * Lâminas de contagem. Lâmina avulsa (sem laboratório) é do dono e de mais ninguém. Lâmina de laboratório
 * obedece a {@link LaboratorioAccessQuery}: o papel global ADMIN não concede acesso e nenhuma regra de
 * participação é reimplementada aqui. Ordem das respostas, para não revelar o estado do laboratório a quem
 * não o enxerga: inexistente/excluída/alheia/ex-membro = 404; vê o laboratório mas não os dados do dono = 403;
 * escrita sem permissão de edição = 403; versão desatualizada = 409.
 */
@Service
@RequiredArgsConstructor
public class LaminaService {

    private static final String ACESSO_NEGADO = "Acesso negado.";
    private static final String ID_COM_OUTRO_CONTEUDO = "Este identificador já foi usado em outra lâmina.";
    private static final String VERSAO_DESATUALIZADA =
            "A lâmina foi alterada por outra operação. Atualize os dados e tente novamente.";

    private final LaminaRepository laminaRepository;
    private final VersaoProtocoloRepository versaoRepository;
    private final LaboratorioAccessQuery laboratorioAccessQuery;
    private final UsuarioRosterQuery usuarioRosterQuery;
    private final Clock clock;

    /**
     * Cria a lâmina ou reconhece a repetição idempotente (ADR 0003). A repetição é resolvida antes de
     * qualquer checagem de acesso e vale também para lâmina já excluída, que não ressuscita. A inserção usa
     * {@code ON CONFLICT DO NOTHING}: quem perde uma corrida pelo mesmo id relê a linha e responde repetição
     * ou conflito, sem abortar a transação.
     */
    @Transactional
    public CriacaoLamina criar(NovaLamina nova, Long usuarioId) {
        String nome = TextoLamina.nome(nova.nome());
        String especie = TextoLamina.especie(nova.especie());
        String tratamento = TextoLamina.tratamento(nova.tratamento());
        String observacoes = TextoLamina.observacoes(nova.observacoes());
        Integer meta = nova.meta();

        var existente = laminaRepository.findById(nova.id());
        if (existente.isPresent()) {
            return repeticao(existente.get(), nova, nome, especie, tratamento, observacoes, usuarioId);
        }

        UUID laboratorioId = nova.laboratorioId();
        if (laboratorioId != null) {
            exigirVisibilidadePropria(laboratorioId, usuarioId);
        }
        if (versaoRepository.findByIdAndPublicadaEmIsNotNull(nova.protocoloVersaoId()).isEmpty()) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }
        if (laboratorioId != null
                && !laboratorioAccessQuery.podeEditarDadosDoPesquisador(laboratorioId, usuarioId, usuarioId)) {
            throw new AccessDeniedException(ACESSO_NEGADO);
        }

        int inseridas = laminaRepository.inserirSeAusente(nova.id(), clock.instant(), usuarioId, laboratorioId,
                nova.protocoloVersaoId(), nome, especie, tratamento, observacoes, meta);
        Lamina gravada = laminaRepository.findById(nova.id())
                .orElseThrow(() -> new ConflictException(ID_COM_OUTRO_CONTEUDO));
        if (inseridas == 1) {
            return new CriacaoLamina(resumir(gravada), true);
        }
        // Outra requisição venceu a corrida pelo mesmo id.
        return repeticao(gravada, nova, nome, especie, tratamento, observacoes, usuarioId);
    }

    /** Id de outro dono é indistinguível de recurso desconhecido. */
    private CriacaoLamina repeticao(Lamina existente, NovaLamina nova, String nome, String especie,
                                    String tratamento, String observacoes, Long usuarioId) {
        if (!usuarioId.equals(existente.getDonoUsuarioId())) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }
        boolean mesmoConteudo = Objects.equals(existente.getLaboratorioId(), nova.laboratorioId())
                && existente.getProtocoloVersaoId().equals(nova.protocoloVersaoId())
                && existente.getNome().equals(nome)
                && existente.getEspecie().equals(especie)
                && existente.getTratamento().equals(tratamento)
                && existente.getObservacoes().equals(observacoes)
                && Objects.equals(existente.getMeta(), nova.meta());
        if (!mesmoConteudo) {
            throw new ConflictException(ID_COM_OUTRO_CONTEUDO);
        }
        return new CriacaoLamina(resumir(existente), false);
    }

    /**
     * Sem {@code laboratorioId}, só as avulsas do próprio usuário. Com ele, só as lâminas dos donos que o
     * leitor pode ver: os donos são descobertos numa consulta e filtrados antes da paginação, para que
     * página e total fiquem corretos.
     */
    @Transactional(readOnly = true)
    public Page<LaminaResumo> listar(UUID laboratorioId, Long donoFiltro, Long usuarioId, Pageable pageable) {
        Page<Lamina> pagina;
        if (laboratorioId == null) {
            pagina = donoFiltro != null && !donoFiltro.equals(usuarioId)
                    ? Page.empty(pageable)
                    : laminaRepository.findAvulsasAtivas(usuarioId, pageable);
        } else {
            exigirVisibilidadePropria(laboratorioId, usuarioId);
            List<Long> permitidos = laminaRepository.findDonosComLaminaAtiva(laboratorioId).stream()
                    .filter(dono -> donoFiltro == null || dono.equals(donoFiltro))
                    .filter(dono -> laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, dono, usuarioId))
                    .toList();
            pagina = permitidos.isEmpty()
                    ? Page.empty(pageable)
                    : laminaRepository.findAtivasDoLaboratorio(laboratorioId, permitidos, pageable);
        }
        return resumir(pagina);
    }

    @Transactional(readOnly = true)
    public LaminaResumo buscar(UUID id, Long usuarioId) {
        return resumir(carregarParaLeitura(id, usuarioId));
    }

    @Transactional
    public LaminaResumo atualizar(UUID id, Long versaoEsperada, String nome, String especie, String tratamento,
                                  String observacoes, Integer meta, Long usuarioId) {
        String nomeFinal = TextoLamina.nome(nome);
        String especieFinal = TextoLamina.especie(especie);
        String tratamentoFinal = TextoLamina.tratamento(tratamento);
        String observacoesFinal = TextoLamina.observacoes(observacoes);

        Lamina lamina = carregarParaEscrita(id, usuarioId);
        exigirVersao(lamina, versaoEsperada);
        lamina.substituirDescricao(nomeFinal, especieFinal, tratamentoFinal, observacoesFinal, meta);
        // O flush leva a falha de versão (corrida entre duas atualizações) para dentro deste método.
        return resumir(laminaRepository.saveAndFlush(lamina));
    }

    /**
     * Exclusão lógica. Repetida pelo dono sobre lâmina já excluída, não tem efeito (204) qualquer que seja a
     * versão. Para qualquer outra pessoa, lâmina excluída é desconhecida (404).
     */
    @Transactional
    public void excluir(UUID id, Long versaoEsperada, Long usuarioId) {
        Lamina lamina = laminaRepository.findById(id).orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
        if (lamina.estaExcluida()) {
            if (usuarioId.equals(lamina.getDonoUsuarioId())) {
                return;
            }
            throw new NotFoundException(NAO_ENCONTRADO);
        }
        exigirEscrita(lamina, usuarioId);
        exigirVersao(lamina, versaoEsperada);
        lamina.excluir(clock.instant());
        laminaRepository.saveAndFlush(lamina);
    }

    private Lamina carregarParaLeitura(UUID id, Long usuarioId) {
        Lamina lamina = laminaRepository.findById(id)
                .filter(candidata -> !candidata.estaExcluida())
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
        exigirLeitura(lamina, usuarioId);
        return lamina;
    }

    private Lamina carregarParaEscrita(UUID id, Long usuarioId) {
        Lamina lamina = laminaRepository.findById(id)
                .filter(candidata -> !candidata.estaExcluida())
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
        exigirEscrita(lamina, usuarioId);
        return lamina;
    }

    private void exigirLeitura(Lamina lamina, Long usuarioId) {
        if (lamina.ehAvulsa()) {
            if (!usuarioId.equals(lamina.getDonoUsuarioId())) {
                throw new NotFoundException(NAO_ENCONTRADO);
            }
            return;
        }
        UUID laboratorioId = lamina.getLaboratorioId();
        exigirVisibilidadePropria(laboratorioId, usuarioId);
        if (!laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, lamina.getDonoUsuarioId(), usuarioId)) {
            throw new AccessDeniedException(ACESSO_NEGADO);
        }
    }

    private void exigirEscrita(Lamina lamina, Long usuarioId) {
        exigirLeitura(lamina, usuarioId);
        if (lamina.ehAvulsa()) {
            return;
        }
        if (!laboratorioAccessQuery.podeEditarDadosDoPesquisador(
                lamina.getLaboratorioId(), lamina.getDonoUsuarioId(), usuarioId)) {
            throw new AccessDeniedException(ACESSO_NEGADO);
        }
    }

    /** Laboratório alheio, inexistente ou do qual o usuário saiu responde 404, sem revelar a diferença. */
    private void exigirVisibilidadePropria(UUID laboratorioId, Long usuarioId) {
        if (!laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, usuarioId, usuarioId)) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }
    }

    private static void exigirVersao(Lamina lamina, Long versaoEsperada) {
        if (!lamina.getVersion().equals(versaoEsperada)) {
            throw new ConflictException(VERSAO_DESATUALIZADA);
        }
    }

    private LaminaResumo resumir(Lamina lamina) {
        return resumir(List.of(lamina)).get(0);
    }

    private Page<LaminaResumo> resumir(Page<Lamina> pagina) {
        List<LaminaResumo> resumos = resumir(pagina.getContent());
        return new PageImpl<>(resumos, pagina.getPageable(), pagina.getTotalElements());
    }

    /** Resolve os nomes dos donos numa única consulta, sem N+1. */
    private List<LaminaResumo> resumir(Collection<Lamina> laminas) {
        List<Long> donoIds = laminas.stream().map(Lamina::getDonoUsuarioId).distinct().toList();
        Map<Long, UsuarioRosterInfo> donos = usuarioRosterQuery.buscarPorIds(donoIds).stream()
                .collect(Collectors.toMap(UsuarioRosterInfo::usuarioId, Function.identity()));
        return laminas.stream().map(lamina -> {
            UsuarioRosterInfo dono = donos.get(lamina.getDonoUsuarioId());
            if (dono == null) {
                throw new IllegalStateException("Conta do dono da lâmina não encontrada.");
            }
            return new LaminaResumo(lamina.getId(), lamina.getVersion(), lamina.getDonoUsuarioId(), dono.nome(),
                    lamina.getLaboratorioId(), lamina.getProtocoloVersaoId(), lamina.getNome(), lamina.getEspecie(),
                    lamina.getTratamento(), lamina.getObservacoes(), lamina.getMeta(),
                    lamina.getCreatedAt(), lamina.getLastModifiedAt());
        }).toList();
    }
}
