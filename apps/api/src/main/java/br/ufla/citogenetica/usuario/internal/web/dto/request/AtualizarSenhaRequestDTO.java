package br.ufla.citogenetica.usuario.internal.web.dto.request;

import jakarta.validation.constraints.NotNull;

public record AtualizarSenhaRequestDTO(

        @NotNull(message = "É obrigatório informar a senha atual") String senhaAtual,

        @NotNull(message = "É obrigatório informar a nova senha") String novaSenha) {
}
