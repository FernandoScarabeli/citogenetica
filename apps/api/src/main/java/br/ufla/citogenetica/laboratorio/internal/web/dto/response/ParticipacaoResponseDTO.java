package br.ufla.citogenetica.laboratorio.internal.web.dto.response;

import java.util.Set;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;
import com.fasterxml.jackson.annotation.JsonInclude;

public record ParticipacaoResponseDTO(UUID id, String nome,
                                      @JsonInclude(JsonInclude.Include.NON_NULL) String email,
                                      Set<PapelLaboratorio> papeis, Long versao) {
}
