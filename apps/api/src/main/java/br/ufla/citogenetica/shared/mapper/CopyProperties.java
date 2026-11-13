package br.ufla.citogenetica.shared.mapper;

import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;
import java.util.Set;

public interface CopyProperties<Entity, RequestDTO> {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "version", ignore = true)
    Entity copyProperties(Entity newEntity, @MappingTarget Entity entity);

    List<Entity> copyProperties(List<Entity> newEntities, @MappingTarget List<Entity> entities);

    Set<Entity> copyProperties(Set<Entity> newEntities, @MappingTarget Set<Entity> entities);
}
