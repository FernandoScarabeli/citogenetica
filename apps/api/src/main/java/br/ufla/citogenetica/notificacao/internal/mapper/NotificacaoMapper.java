package br.ufla.citogenetica.notificacao.internal.mapper;

import br.ufla.citogenetica.notificacao.internal.domain.Notificacao;
import br.ufla.citogenetica.notificacao.internal.web.dto.response.NotificacaoResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificacaoMapper {

    @Mapping(source = "createdDate", target = "dataCriacao")
    NotificacaoResponseDTO toResponseDTO(Notificacao entity);
}

