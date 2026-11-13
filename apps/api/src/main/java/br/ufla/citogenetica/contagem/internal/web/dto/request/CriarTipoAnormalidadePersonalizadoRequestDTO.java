package br.ufla.citogenetica.contagem.internal.web.dto.request;

import br.ufla.citogenetica.shared.validation.CanonicalUuidV4;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarTipoAnormalidadePersonalizadoRequestDTO(
        @Schema(description = "UUIDv4 canônico gerado pelo cliente; repetir o mesmo id com o mesmo conteúdo é idempotente")
        @NotNull(message = "Informe o identificador do tipo.")
        @CanonicalUuidV4(message = "O identificador do tipo deve ser um UUIDv4 canônico.")
        String id,

        @Schema(description = "Fase (de uma versão publicada) à qual o tipo pertence")
        @NotNull(message = "Informe a fase.")
        @CanonicalUuidV4(message = "A fase deve ser um UUIDv4 canônico.")
        String faseId,

        @Schema(description = "Laboratório do escopo; ausente significa lâmina avulsa", nullable = true)
        @CanonicalUuidV4(message = "O laboratório deve ser um UUIDv4 canônico.")
        String laboratorioId,

        @Schema(description = "Nome do tipo; espaços repetidos são colapsados e as pontas removidas")
        @NotBlank(message = "Informe o nome do tipo de anormalidade.")
        @Size(max = 120, message = "O nome do tipo de anormalidade deve ter no máximo 120 caracteres.")
        String nome) {
}
