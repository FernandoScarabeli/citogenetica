package br.ufla.citogenetica.contagem.internal.web.dto.response;

import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.application.RegistroDesfeito;
import br.ufla.citogenetica.contagem.internal.application.ResultadoDesfazer;
import io.swagger.v3.oas.annotations.media.Schema;

public record DesfazerRegistroResponseDTO(
        @Schema(description = "Id da operação, o mesmo enviado pelo cliente")
        UUID id,
        @Schema(description = "Células removidas, igual à quantidade pedida")
        int quantidadeDesfeita,
        @Schema(description = "Registros afetados, do mais recente ao mais antigo")
        List<RegistroDesfeitoDTO> registros) {

    public record RegistroDesfeitoDTO(
            UUID id,
            @Schema(description = "Células que sobraram no registro; 0 quando ele foi excluído por inteiro")
            int quantidadeRestante,
            @Schema(description = "Verdadeiro quando o registro foi excluído por inteiro")
            boolean excluido) {

        static RegistroDesfeitoDTO de(RegistroDesfeito registro) {
            return new RegistroDesfeitoDTO(registro.id(), registro.quantidadeRestante(), registro.excluido());
        }
    }

    public static DesfazerRegistroResponseDTO de(ResultadoDesfazer resultado) {
        return new DesfazerRegistroResponseDTO(resultado.id(), resultado.quantidadeDesfeita(),
                resultado.registros().stream().map(RegistroDesfeitoDTO::de).toList());
    }
}
