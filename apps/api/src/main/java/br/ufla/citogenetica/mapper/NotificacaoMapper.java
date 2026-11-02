package br.ufla.citogenetica.mapper;

import br.ufla.citogenetica.model.notificacao.Notificacao;
import br.ufla.citogenetica.model.notificacao.dto.NotificacaoResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificacaoMapper {

    @Mapping(source = "createdDate", target = "dataCriacao")
    NotificacaoResponseDTO toResponseDTO(Notificacao entity);
}

