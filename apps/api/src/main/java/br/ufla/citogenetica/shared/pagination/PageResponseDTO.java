package br.ufla.citogenetica.shared.pagination;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@Getter
public class PageResponseDTO<Entity, ResponseDTO> {
    private final List<ResponseDTO> content;
    private final int currentPage;
    private final int totalPages;
    private final long totalElements;

    public PageResponseDTO(Page<Entity> pageResult, Function<Entity, ResponseDTO> mapperFunction) {
        if (pageResult != null && !pageResult.isEmpty()) {
            this.content = pageResult.getContent().stream().map(mapperFunction).toList();
            this.currentPage = pageResult.getPageable().getPageNumber();
            this.totalPages = pageResult.getTotalPages();
            this.totalElements = pageResult.getTotalElements();
        } else {
            this.content = Collections.emptyList();
            this.currentPage = 0;
            this.totalPages = 0;
            this.totalElements = 0;
        }
    }
}
