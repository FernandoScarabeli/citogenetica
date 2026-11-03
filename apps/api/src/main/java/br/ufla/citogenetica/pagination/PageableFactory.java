package br.ufla.citogenetica.pagination;

import br.ufla.citogenetica.exception.FiltroInvalidoException;
import br.ufla.citogenetica.filter.CamposPermitidos;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

public final class PageableFactory {

    private static final Set<String> DIRECOES_ORDENACAO = Set.of("asc", "desc");

    private PageableFactory() {
    }

    public static Pageable criar(int page, int size) {
        return PageRequest.of(page, size);
    }

    public static Pageable criar(int page, int size, String sortBy, String sortDirection,
                                 CamposPermitidos camposPermitidos) {
        if (sortBy != null && !sortBy.isBlank() && !camposPermitidos.ordenacao().contains(sortBy)) {
            throw new FiltroInvalidoException("Campo de ordenação não permitido: '" + sortBy + "'.");
        }

        String direcao = sortDirection == null ? "asc" : sortDirection.toLowerCase(Locale.ROOT);
        if (!DIRECOES_ORDENACAO.contains(direcao)) {
            throw new FiltroInvalidoException("Direção de ordenação inválida: use 'asc' ou 'desc'.");
        }

        Sort sort = sortBy == null || sortBy.isBlank()
                ? Sort.unsorted()
                : Sort.by("desc".equals(direcao) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        return PageRequest.of(page, size, sort);
    }
}
