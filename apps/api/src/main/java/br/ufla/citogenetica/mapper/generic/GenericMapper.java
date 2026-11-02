package br.ufla.citogenetica.mapper.generic;

public interface GenericMapper<Entity, RequestDTO, ResponseDTO>
        extends
        EntityToResponse<Entity, ResponseDTO>,
        RequestToEntity<Entity, RequestDTO>,
        CopyProperties<Entity, RequestDTO> {
}