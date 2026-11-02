package br.ufla.citogenetica.mapper.generic;

import java.util.List;
import java.util.Set;

public interface EntityToResponse<Entity, ResponseDTO> {
    ResponseDTO toResponseDTO(Entity entity);
    List<ResponseDTO> toResponseDTO(List<Entity> entities);
    Set<ResponseDTO> entityToResponse(Set<Entity> entities);
}