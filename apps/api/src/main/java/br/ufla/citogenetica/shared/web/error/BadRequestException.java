package br.ufla.citogenetica.shared.web.error;

/** Requisição recusada com mensagem segura para exibição, como link de ação inválido ou expirado. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
