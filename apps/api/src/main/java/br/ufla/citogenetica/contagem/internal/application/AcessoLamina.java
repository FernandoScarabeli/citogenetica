package br.ufla.citogenetica.contagem.internal.application;

import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.Lamina;
import br.ufla.citogenetica.contagem.internal.persistence.LaminaRepository;
import br.ufla.citogenetica.laboratorio.api.LaboratorioAccessQuery;
import br.ufla.citogenetica.shared.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import static br.ufla.citogenetica.contagem.internal.application.ProtocoloService.NAO_ENCONTRADO;

/**
 * Regra única de acesso a uma lâmina, usada por todos os serviços do módulo. Lâmina avulsa (sem laboratório) é do
 * dono e de mais ninguém. Lâmina de laboratório obedece a {@link LaboratorioAccessQuery}: o papel global ADMIN não
 * concede acesso e nenhuma regra de participação é reimplementada aqui. Ordem das respostas, para não revelar o
 * estado do laboratório a quem não o enxerga: inexistente/excluída/alheia/ex-membro = 404; vê o laboratório mas não
 * os dados do dono = 403; escrita sem permissão de edição = 403.
 */
@Component
@RequiredArgsConstructor
public class AcessoLamina {

    private static final String ACESSO_NEGADO = "Acesso negado.";

    private final LaminaRepository laminaRepository;
    private final LaboratorioAccessQuery laboratorioAccessQuery;

    /** Carrega a lâmina ativa e exige permissão de leitura. */
    public Lamina carregarParaLeitura(UUID id, Long usuarioId) {
        Lamina lamina = carregarAtiva(id);
        exigirLeitura(lamina, usuarioId);
        return lamina;
    }

    /** Carrega a lâmina ativa e exige permissão de escrita. */
    public Lamina carregarParaEscrita(UUID id, Long usuarioId) {
        Lamina lamina = carregarAtiva(id);
        exigirEscrita(lamina, usuarioId);
        return lamina;
    }

    public void exigirEscrita(Lamina lamina, Long usuarioId) {
        exigirLeitura(lamina, usuarioId);
        if (lamina.ehAvulsa()) {
            return;
        }
        exigirEdicaoNoLaboratorio(lamina.getLaboratorioId(), lamina.getDonoUsuarioId(), usuarioId);
    }

    /** Laboratório alheio, inexistente ou do qual o usuário saiu responde 404, sem revelar a diferença. */
    public void exigirVisibilidadePropria(UUID laboratorioId, Long usuarioId) {
        if (!laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, usuarioId, usuarioId)) {
            throw new NotFoundException(NAO_ENCONTRADO);
        }
    }

    /** 403 se o usuário não pode editar os dados do pesquisador no laboratório (por exemplo, laboratório desativado). */
    public void exigirEdicaoNoLaboratorio(UUID laboratorioId, Long pesquisadorId, Long usuarioId) {
        if (!laboratorioAccessQuery.podeEditarDadosDoPesquisador(laboratorioId, pesquisadorId, usuarioId)) {
            throw new AccessDeniedException(ACESSO_NEGADO);
        }
    }

    private Lamina carregarAtiva(UUID id) {
        return laminaRepository.findById(id)
                .filter(candidata -> !candidata.estaExcluida())
                .orElseThrow(() -> new NotFoundException(NAO_ENCONTRADO));
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
}
