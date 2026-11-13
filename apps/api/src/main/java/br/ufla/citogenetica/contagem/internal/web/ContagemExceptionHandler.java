package br.ufla.citogenetica.contagem.internal.web;

import java.util.List;

import br.ufla.citogenetica.shared.web.error.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz falhas de entrada e de constraint do módulo: nunca viram 500 nem expõem nome de constraint.
 * O serviço já trata a corrida por nome ou id; este conflito genérico cobre qualquer outra violação.
 */
@RestControllerAdvice(basePackageClasses = ProtocoloController.class)
@Order(0)
public class ContagemExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleConstraintViolation(HttpServletRequest request) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),
                "A operação não pode ser concluída.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDTO> handleParametroAusente(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Parâmetros inválidos", request.getRequestURI(),
                List.of(ex.getParameterName() + ": parâmetro obrigatório"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleCorpoIlegivel(HttpServletRequest request) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Corpo da requisição ausente ou inválido.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
