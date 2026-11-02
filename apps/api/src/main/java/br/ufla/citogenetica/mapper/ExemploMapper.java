package br.ufla.citogenetica.mapper;


import br.ufla.citogenetica.mapper.generic.GenericMapper;
import br.ufla.citogenetica.model.crudExemplo.Exemplo;
import br.ufla.citogenetica.model.crudExemplo.dto.request.ExemploRequestDTO;
import br.ufla.citogenetica.model.crudExemplo.dto.response.ExemploResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ExemploMapper extends GenericMapper<Exemplo, ExemploRequestDTO, ExemploResponseDTO> {

}
