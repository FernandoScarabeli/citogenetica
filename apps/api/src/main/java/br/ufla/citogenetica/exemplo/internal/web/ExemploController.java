package br.ufla.citogenetica.exemplo.internal.web;

import br.ufla.citogenetica.exemplo.internal.application.ExemploService;
import br.ufla.citogenetica.exemplo.internal.domain.Exemplo;
import br.ufla.citogenetica.exemplo.internal.mapper.ExemploMapper;
import br.ufla.citogenetica.exemplo.internal.web.dto.request.ExemploRequestDTO;
import br.ufla.citogenetica.exemplo.internal.web.dto.response.ExemploResponseDTO;
import br.ufla.citogenetica.shared.filter.CamposPermitidos;
import br.ufla.citogenetica.shared.filter.CriteriaCreator;
import br.ufla.citogenetica.shared.pagination.PageResponseDTO;
import br.ufla.citogenetica.shared.pagination.PaginationConstants;
import br.ufla.citogenetica.shared.pagination.PageableFactory;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/** CRUD vertical mantido como padrão de referência para novos módulos. */
@RestController
@RequestMapping("/api/v1/exemplos")
@RequiredArgsConstructor
@SecurityRequirement(name = "sessao-cookie")
public class ExemploController {

    private static final CamposPermitidos CAMPOS_LISTAGEM = new CamposPermitidos(
            Set.of("id", "enumType", "descricao", "valor", "dataExemplo", "ativo", "createdDate"),
            Set.of("id", "enumType", "descricao", "valor", "dataExemplo", "ativo", "createdDate"));

    private final ExemploService exemploService;
    private final ExemploMapper exemploMapper;

    @GetMapping
    public ResponseEntity<PageResponseDTO<Exemplo, ExemploResponseDTO>> listarExemplos(
            // Parâmetros de filtro dinâmico
            @RequestParam MultiValueMap<String, String> params,

            // Parâmetros de paginação e ordenação recebidos individualmente
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "a página não pode ser negativa") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "o tamanho da página deve ser no mínimo 1")
            @Max(value = PaginationConstants.MAX_PAGE_SIZE,
                    message = "o tamanho da página deve ser no máximo {value}") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {


        Specification<Exemplo> spec = CriteriaCreator.byFilterMap(params, CAMPOS_LISTAGEM);

        Pageable pageable = PageableFactory.criar(page, size, sortBy, sortDirection, CAMPOS_LISTAGEM);

        Page<Exemplo> pageResult = exemploService.buscarTodos(spec, pageable);

        PageResponseDTO<Exemplo, ExemploResponseDTO> response = new PageResponseDTO<>(pageResult, exemploMapper::toResponseDTO);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExemploResponseDTO> buscarExemploPorId(@PathVariable Long id) {
        Exemplo exemplo = exemploService.buscarPorId(id);
        return ResponseEntity.status(HttpStatus.OK).body(exemploMapper.toResponseDTO(exemplo));
    }

    @PostMapping
    @SecurityRequirement(name = "csrf-token")
    public ResponseEntity<ExemploResponseDTO> criarExemplo(@RequestBody @Valid ExemploRequestDTO requestDTO) {
        Exemplo novoExemplo = exemploService.criar(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(exemploMapper.toResponseDTO(novoExemplo));
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "csrf-token")
    public ResponseEntity<ExemploResponseDTO> atualizarExemplo(@PathVariable Long id, @RequestBody @Valid ExemploRequestDTO requestDTO) {
        Exemplo exemploAtualizado = exemploService.atualizar(id, requestDTO);
        return ResponseEntity.status(HttpStatus.OK).body(exemploMapper.toResponseDTO(exemploAtualizado));
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "csrf-token")
    public ResponseEntity<Void> deletarExemplo(@PathVariable Long id) {
        exemploService.deletar(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
