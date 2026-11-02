package br.ufla.citogenetica.controller;

import br.ufla.citogenetica.filter.CriteriaCreator;
import br.ufla.citogenetica.mapper.ExemploMapper;
import br.ufla.citogenetica.model.crudExemplo.Exemplo;
import br.ufla.citogenetica.model.crudExemplo.dto.request.ExemploRequestDTO;
import br.ufla.citogenetica.model.crudExemplo.dto.response.ExemploResponseDTO;
import br.ufla.citogenetica.model.genericDto.PageResponseDTO;
import br.ufla.citogenetica.service.ExemploService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/exemplos")
@RequiredArgsConstructor
public class ExemploController {

    private final ExemploService exemploService;
    private final ExemploMapper exemploMapper;

    @GetMapping
    public ResponseEntity<PageResponseDTO<Exemplo, ExemploResponseDTO>> listarExemplos(
            // Parâmetros de filtro dinâmico
            @RequestParam MultiValueMap<String, String> params,

            // Parâmetros de paginação e ordenação recebidos individualmente
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {


        Specification<Exemplo> spec = CriteriaCreator.byFilterMap(params, Exemplo.class, sortBy, sortDirection);

        Pageable pageable = PageRequest.of(page, size);

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
    public ResponseEntity<ExemploResponseDTO> criarExemplo(@RequestBody @Valid ExemploRequestDTO requestDTO) {
        Exemplo novoExemplo = exemploService.criar(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(exemploMapper.toResponseDTO(novoExemplo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExemploResponseDTO> atualizarExemplo(@PathVariable Long id, @RequestBody @Valid ExemploRequestDTO requestDTO) {
        Exemplo exemploAtualizado = exemploService.atualizar(id, requestDTO);
        return ResponseEntity.status(HttpStatus.OK).body(exemploMapper.toResponseDTO(exemploAtualizado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarExemplo(@PathVariable Long id) {
        exemploService.deletar(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}