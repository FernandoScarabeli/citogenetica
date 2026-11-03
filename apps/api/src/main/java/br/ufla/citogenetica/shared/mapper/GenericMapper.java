package br.ufla.citogenetica.shared.mapper;

public interface GenericMapper<Entity, RequestDTO, ResponseDTO>
        extends
        EntityToResponse<Entity, ResponseDTO>,
        RequestToEntity<Entity, RequestDTO>,
        CopyProperties<Entity, RequestDTO> {
}
