package br.ufla.citogenetica.contagem.internal.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.FaseProtocolo;
import br.ufla.citogenetica.contagem.internal.domain.TipoAnormalidade;
import br.ufla.citogenetica.contagem.internal.persistence.FaseProtocoloRepository;
import br.ufla.citogenetica.contagem.internal.persistence.TipoAnormalidadeRepository;
import br.ufla.citogenetica.contagem.internal.persistence.VersaoProtocoloRepository;
import br.ufla.citogenetica.laboratorio.api.LaboratorioAccessQuery;
import br.ufla.citogenetica.shared.web.error.BadRequestException;
import br.ufla.citogenetica.shared.web.error.ConflictException;
import br.ufla.citogenetica.shared.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static br.ufla.citogenetica.contagem.internal.application.ProtocoloService.NAO_ENCONTRADO;

/**
 * Tipos de anormalidade personalizados: pertencem ao usuário, a uma fase de versão publicada e,
 * opcionalmente, a um laboratório (sem laboratório = lâmina avulsa, visível só ao dono).
 * O papel global ADMIN não concede acesso; só a participação no laboratório conta.
 */
@Service
@RequiredArgsConstructor
public class TipoAnormalidadePersonalizadoService {

    private static final String NOME_DUPLICADO = "Já existe um tipo de anormalidade com este nome nesta fase.";
    private static final String ID_COM_OUTRO_CONTEUDO =
            "Este identificador já foi usado em outro tipo de anormalidade.";

    private final TipoAnormalidadeRepository tipoRepository;
    private final FaseProtocoloRepository faseRepository;
    private final VersaoProtocoloRepository versaoRepository;
    private final LaboratorioAccessQuery laboratorioAccessQuery;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TipoPersonalizadoResumo> listar(UUID versaoId, UUID laboratorioId, Long usuarioId) {
        if (versaoRepository.findByIdAndPublicadaEmIsNotNull(versaoId).isEmpty()) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }
        List<TipoAnormalidade> tipos;
        if (laboratorioId == null) {
            tipos = tipoRepository.findPersonalizadosAvulsos(versaoId, usuarioId);
        } else {
            exigirVisibilidade(laboratorioId, usuarioId);
            tipos = tipoRepository.findPersonalizadosDoLaboratorio(versaoId, usuarioId, laboratorioId);
        }
        return tipos.stream().map(TipoAnormalidadePersonalizadoService::resumo).toList();
    }

    /**
     * Cria o tipo ou reconhece a repetição idempotente (ADR 0003). A inserção usa
     * {@code ON CONFLICT DO NOTHING}: requisições simultâneas com o mesmo id ou o mesmo nome não
     * abortam a transação; quem perde a corrida relê o id e responde repetição ou conflito.
     */
    @Transactional
    public CriacaoTipoPersonalizado criar(UUID id, UUID faseId, UUID laboratorioId, String nome, Long usuarioId) {
        String nomeExibido = NomeTipoAnormalidade.exibicao(nome);
        if (NomeTipoAnormalidade.contemCaractereInvisivelOuDeControle(nomeExibido)) {
            throw new BadRequestException("O nome do tipo de anormalidade não pode conter caracteres de controle ou invisíveis.");
        }
        String nomeNormalizado = NomeTipoAnormalidade.normalizado(nome);
        if (nomeNormalizado.isEmpty()) {
            throw new BadRequestException("Informe o nome do tipo de anormalidade.");
        }

        var existente = tipoRepository.findById(id);
        if (existente.isPresent()) {
            return repeticao(existente.get(), faseId, laboratorioId, nomeExibido, usuarioId);
        }

        exigirFaseDeVersaoPublicada(faseId);
        if (laboratorioId != null) {
            exigirVisibilidade(laboratorioId, usuarioId);
            if (!laboratorioAccessQuery.podeEditarDadosDoPesquisador(laboratorioId, usuarioId, usuarioId)) {
                throw new AccessDeniedException("Acesso negado.");
            }
        }
        if (tipoRepository.existsByFaseIdAndNomeNormalizadoAndUsuarioIdIsNull(faseId, nomeNormalizado)
                || existePersonalizadoNoEscopo(faseId, usuarioId, laboratorioId, nomeNormalizado)) {
            return resolverConflito(id, faseId, laboratorioId, nomeExibido, usuarioId);
        }

        int inseridos = tipoRepository.inserirPersonalizadoSeAusente(
                id, clock.instant(), faseId, usuarioId, laboratorioId, nomeExibido, nomeNormalizado);
        if (inseridos == 1) {
            return new CriacaoTipoPersonalizado(new TipoPersonalizadoResumo(id, faseId, laboratorioId, nomeExibido), true);
        }
        // Outra requisição venceu a corrida: mesmo id (repetição ou conflito) ou mesmo nome (conflito).
        return resolverConflito(id, faseId, laboratorioId, nomeExibido, usuarioId);
    }

    /**
     * Nome já ocupado: se o ocupante é o próprio id (gravado por uma requisição simultânea idêntica), é repetição;
     * caso contrário, conflito de nome.
     */
    private CriacaoTipoPersonalizado resolverConflito(UUID id, UUID faseId, UUID laboratorioId, String nomeExibido,
                                                      Long usuarioId) {
        return tipoRepository.findById(id)
                .map(ocupante -> repeticao(ocupante, faseId, laboratorioId, nomeExibido, usuarioId))
                .orElseThrow(() -> new ConflictException(NOME_DUPLICADO));
    }

    /** Id de outro dono é indistinguível de recurso desconhecido; tipo fixo também (nunca tem dono). */
    private static CriacaoTipoPersonalizado repeticao(TipoAnormalidade existente, UUID faseId, UUID laboratorioId,
                                                      String nomeExibido, Long usuarioId) {
        if (!usuarioId.equals(existente.getUsuarioId())) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }
        boolean mesmoConteudo = existente.getFaseId().equals(faseId)
                && Objects.equals(existente.getLaboratorioId(), laboratorioId)
                && existente.getNome().equals(nomeExibido);
        if (!mesmoConteudo) {
            throw new ConflictException(ID_COM_OUTRO_CONTEUDO);
        }
        return new CriacaoTipoPersonalizado(resumo(existente), false);
    }

    private void exigirFaseDeVersaoPublicada(UUID faseId) {
        FaseProtocolo fase = faseRepository.findById(faseId)
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
        if (versaoRepository.findByIdAndPublicadaEmIsNotNull(fase.getVersaoId()).isEmpty()) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }
    }

    /** Laboratório alheio ou inexistente responde 404, sem revelar a diferença. */
    private void exigirVisibilidade(UUID laboratorioId, Long usuarioId) {
        if (!laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, usuarioId, usuarioId)) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }
    }

    private boolean existePersonalizadoNoEscopo(UUID faseId, Long usuarioId, UUID laboratorioId, String normalizado) {
        return laboratorioId == null
                ? tipoRepository.existsByFaseIdAndUsuarioIdAndLaboratorioIdIsNullAndNomeNormalizado(
                        faseId, usuarioId, normalizado)
                : tipoRepository.existsByFaseIdAndUsuarioIdAndLaboratorioIdAndNomeNormalizado(
                        faseId, usuarioId, laboratorioId, normalizado);
    }

    private static TipoPersonalizadoResumo resumo(TipoAnormalidade tipo) {
        return new TipoPersonalizadoResumo(tipo.getId(), tipo.getFaseId(), tipo.getLaboratorioId(), tipo.getNome());
    }
}
