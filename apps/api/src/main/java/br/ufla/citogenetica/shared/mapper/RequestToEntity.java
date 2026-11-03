package br.ufla.citogenetica.shared.mapper;

import br.ufla.citogenetica.shared.web.dto.GenericDTO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import java.util.List;
import java.util.Set;

@Validated
public interface RequestToEntity<Entity, RequestDTO> {
    Entity requestToEntity(@Valid RequestDTO requestDTO);

    List<Entity> requestToEntity(List<@Valid RequestDTO> requestDTO);

    Set<Entity> requestToEntity(Set<@Valid RequestDTO> requestDTO);

    Entity genericToEntity(@Valid GenericDTO genericDTO);

    List<Entity> genericToEntity(List<@Valid GenericDTO> genericDTO);

    Set<Entity> genericToEntity(Set<@Valid GenericDTO> genericDTO);
}
