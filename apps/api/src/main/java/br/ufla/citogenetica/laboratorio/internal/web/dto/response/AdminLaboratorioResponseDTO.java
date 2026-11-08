package br.ufla.citogenetica.laboratorio.internal.web.dto.response;

import java.util.List;
import java.util.UUID;

import br.ufla.citogenetica.laboratorio.internal.application.AdminLaboratorioResumo;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoLaboratorio;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resumo de laboratório visível ao administrador da plataforma.")
public record AdminLaboratorioResponseDTO(
        @Schema(description = "Identificador do laboratório.")
        UUID id,
        @Schema(description = "Nome do laboratório.")
        String nome,
        @Schema(description = "Estado do laboratório: ATIVO ou DESATIVADO.")
        EstadoLaboratorio estado,
        @Schema(description = "Quantidade de participações ativas no laboratório.")
        long quantidadeMembrosAtivos,
        @Schema(description = "Administradores locais com participação ativa e papel ADMIN vigente.")
        List<AdministradorResponseDTO> administradores) {

    public AdminLaboratorioResponseDTO {
        administradores = List.copyOf(administradores);
    }

    public static AdminLaboratorioResponseDTO de(AdminLaboratorioResumo resumo) {
        List<AdministradorResponseDTO> administradores = resumo.administradores().stream()
                .map(administrador -> new AdministradorResponseDTO(administrador.nome(), administrador.email()))
                .toList();
        return new AdminLaboratorioResponseDTO(resumo.id(), resumo.nome(), resumo.estado(),
                resumo.quantidadeMembrosAtivos(), administradores);
    }

    @Schema(description = "Identidade básica de um administrador local ativo.")
    public record AdministradorResponseDTO(
            @Schema(description = "Nome do administrador.") String nome,
            @Schema(description = "E-mail do administrador.") String email) {
    }
}
