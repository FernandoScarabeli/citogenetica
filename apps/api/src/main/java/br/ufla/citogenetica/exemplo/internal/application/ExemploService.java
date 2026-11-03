package br.ufla.citogenetica.exemplo.internal.application;

import br.ufla.citogenetica.exemplo.internal.domain.Exemplo;
import br.ufla.citogenetica.exemplo.internal.mapper.ExemploMapper;
import br.ufla.citogenetica.exemplo.internal.persistence.ExemploRepository;
import br.ufla.citogenetica.exemplo.internal.web.dto.request.ExemploRequestDTO;
import br.ufla.citogenetica.shared.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExemploService {

    private final ExemploRepository exemploRepository;
    private final ExemploMapper exemploMapper;

    @Transactional(readOnly = true)
    public Page<Exemplo> buscarTodos(Specification<Exemplo> spec, Pageable pageable) {
        return exemploRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Exemplo buscarPorId(Long id) {
        return exemploRepository.findById(id)
                // Aqui usa uma exceção customizada
                .orElseThrow(() -> new NotFoundException(Exemplo.class, id));
    }

    @Transactional
    public Exemplo criar(ExemploRequestDTO requestDTO) {
        Exemplo entidade = exemploMapper.requestToEntity(requestDTO);
        // Aqui você poderia adicionar lógicas de negócio antes de salvar
        return exemploRepository.save(entidade);
    }

    @Transactional
    public Exemplo atualizar(Long id, ExemploRequestDTO requestDTO) {
        // Busca a entidade existente no banco!!
        Exemplo entidadeExistente = this.buscarPorId(id);

        // Converte o DTO para uma nova entidade com os dados da requisição
        Exemplo entidadeComNovosDados = exemploMapper.requestToEntity(requestDTO);

        // Usa o mapper para copiar as propriedades atualizadas para a entidade existente
        exemploMapper.copyProperties(entidadeComNovosDados, entidadeExistente);

        // Salva a entidade atualizada
        return exemploRepository.save(entidadeExistente);
    }

    @Transactional
    public void deletar(Long id) {
        Exemplo entidade = this.buscarPorId(id);
        exemploRepository.delete(entidade);
    }
}
