package br.ufla.citogenetica.usuario.internal.mapper;

import br.ufla.citogenetica.shared.mapper.GenericMapper;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.web.dto.request.AtualizarUsuarioRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.UsuarioRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.response.UsuarioResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface UsuarioMapper extends GenericMapper<Usuario, UsuarioRequestDTO, UsuarioResponseDTO> {

    @Override
    @Mapping(target = "fotoVersao", expression = "java(fotoVersao(entity.getFotoChave()))")
    UsuarioResponseDTO toResponseDTO(Usuario entity);

    /** A versão é o identificador aleatório da chave, sem a pasta nem a extensão. @Named evita que o MapStruct a use em outros textos. */
    @Named("fotoVersao")
    default String fotoVersao(String fotoChave) {
        if (fotoChave == null) {
            return null;
        }
        String nome = fotoChave.substring(fotoChave.lastIndexOf('/') + 1);
        return nome.substring(0, nome.indexOf('.'));
    }

    @Override
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "emailConfirmadoEm", ignore = true)
    @Mapping(target = "fotoChave", ignore = true)
    Usuario requestToEntity(UsuarioRequestDTO DTO);

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "senha", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    @Mapping(target = "emailConfirmadoEm", ignore = true)
    @Mapping(target = "fotoChave", ignore = true)
    Usuario copyProperties(Usuario newEntity, @MappingTarget Usuario entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "senha", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    @Mapping(target = "emailConfirmadoEm", ignore = true)
    @Mapping(target = "fotoChave", ignore = true)
    void copyProperties(AtualizarUsuarioRequestDTO dto, @MappingTarget Usuario entity);
}
