package br.ufla.citogenetica.model.usuario.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizarSenhaRequestDTO(

        @NotBlank(message = "É obrigatório informar a senha atual") String senhaAtual,

        @NotBlank(message = "É obrigatório informar a nova senha") @Size(min = 8, message = "A nova senha deve ter no mínimo 8 caracteres") String novaSenha) {
}