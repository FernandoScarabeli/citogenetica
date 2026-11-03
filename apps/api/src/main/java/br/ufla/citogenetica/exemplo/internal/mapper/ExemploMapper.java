package br.ufla.citogenetica.exemplo.internal.mapper;

import br.ufla.citogenetica.exemplo.internal.domain.Exemplo;
import br.ufla.citogenetica.exemplo.internal.web.dto.request.ExemploRequestDTO;
import br.ufla.citogenetica.exemplo.internal.web.dto.response.ExemploResponseDTO;
import br.ufla.citogenetica.shared.mapper.GenericMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ExemploMapper extends GenericMapper<Exemplo, ExemploRequestDTO, ExemploResponseDTO> {

}
