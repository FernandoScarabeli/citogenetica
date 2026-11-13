package br.ufla.citogenetica.contagem.internal.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import br.ufla.citogenetica.contagem.internal.domain.ComentarioLamina;
import br.ufla.citogenetica.contagem.internal.domain.Lamina;
import br.ufla.citogenetica.contagem.internal.persistence.ComentarioLaminaRepository;
import br.ufla.citogenetica.shared.persistence.UuidFactory;
import br.ufla.citogenetica.shared.web.error.BusinessException;
import br.ufla.citogenetica.shared.web.error.NotFoundException;
import br.ufla.citogenetica.usuario.api.UsuarioRosterInfo;
import br.ufla.citogenetica.usuario.api.UsuarioRosterQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comentários de uma lâmina. Quem vê a lâmina vê os comentários. Comentar não exige poder editar a lâmina
 * (o orientador comenta na lâmina do aluno), mas exige participação ativa num laboratório ativo: a mesma condição
 * para editar os próprios dados nele. Só o autor apaga o próprio comentário.
 */
@Service
@RequiredArgsConstructor
public class ComentarioLaminaService {

    public static final int TEXTO_MAXIMO = 2000;
    public static final int MAXIMO_POR_LAMINA = 1000;
    private static final String COMENTARIO_NAO_ENCONTRADO = "Comentário não encontrado.";

    private final AcessoLamina acesso;
    private final ComentarioLaminaRepository repository;
    private final UsuarioRosterQuery usuarioRosterQuery;
    private final UuidFactory uuidFactory;

    public record Comentario(UUID id, Long autorUsuarioId, String autorNome, String texto, Instant criadoEm) {}

    @Transactional(readOnly = true)
    public List<Comentario> listar(UUID laminaId, Long usuarioId) {
        acesso.carregarParaLeitura(laminaId, usuarioId);
        return comNomes(repository.findByLaminaIdOrderByCreatedAtAscIdAsc(laminaId));
    }

    @Transactional
    public Comentario comentar(UUID laminaId, String textoBruto, Long usuarioId) {
        Lamina lamina = carregarParaComentar(laminaId, usuarioId);
        String texto = textoBruto == null ? "" : textoBruto.strip();
        if (texto.isEmpty()) {
            throw new BusinessException("Escreva o comentário.");
        }
        if (texto.length() > TEXTO_MAXIMO) {
            throw new BusinessException("O comentário pode ter até " + TEXTO_MAXIMO + " caracteres.");
        }
        if (repository.countByLaminaId(lamina.getId()) >= MAXIMO_POR_LAMINA) {
            throw new BusinessException("Esta lâmina chegou ao limite de comentários.");
        }
        ComentarioLamina comentario = repository.saveAndFlush(
                new ComentarioLamina(uuidFactory.generate(), lamina.getId(), usuarioId, texto));
        return comNomes(List.of(comentario)).get(0);
    }

    @Transactional
    public void apagar(UUID laminaId, UUID comentarioId, Long usuarioId) {
        carregarParaComentar(laminaId, usuarioId);
        ComentarioLamina comentario = repository.findByIdAndLaminaId(comentarioId, laminaId)
                .orElseThrow(() -> new NotFoundException(COMENTARIO_NAO_ENCONTRADO));
        if (!comentario.getAutorUsuarioId().equals(usuarioId)) {
            throw new AccessDeniedException("Só quem escreveu pode apagar o comentário.");
        }
        repository.delete(comentario);
    }

    private Lamina carregarParaComentar(UUID laminaId, Long usuarioId) {
        Lamina lamina = acesso.carregarParaLeitura(laminaId, usuarioId);
        if (!lamina.ehAvulsa()) {
            acesso.exigirEdicaoNoLaboratorio(lamina.getLaboratorioId(), usuarioId, usuarioId);
        }
        return lamina;
    }

    private List<Comentario> comNomes(List<ComentarioLamina> comentarios) {
        Map<Long, String> nomes = usuarioRosterQuery.buscarPorIds(comentarios.stream()
                        .map(ComentarioLamina::getAutorUsuarioId).distinct().toList()).stream()
                .collect(Collectors.toMap(UsuarioRosterInfo::usuarioId, UsuarioRosterInfo::nome, (a, b) -> a));
        return comentarios.stream().map(comentario -> new Comentario(comentario.getId(), comentario.getAutorUsuarioId(),
                nomes.getOrDefault(comentario.getAutorUsuarioId(), ""), comentario.getTexto(),
                comentario.getCreatedAt())).toList();
    }
}
