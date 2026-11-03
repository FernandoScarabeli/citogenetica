package br.ufla.citogenetica.laboratorio.internal.web;

import br.ufla.citogenetica.shared.web.error.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Constraints concorrentes do módulo são conflitos de estado, nunca erros internos 500. */
@RestControllerAdvice(basePackageClasses = LaboratorioController.class)
@Order(0)
public class LaboratorioExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleConstraintViolation(HttpServletRequest request) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),
                "A operação não pode ser concluída.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
}
