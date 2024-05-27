package br.com.microservices.orchestrated.paymentservice.config.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ExceptionGlobalHandler {
    @ExceptionHandler
    public ResponseEntity<?> handleValidationException(ValidationException exception) {
        var details = new ExceptionDetails(HttpStatus.BAD_GATEWAY.value(), exception.getMessage());
        return new ResponseEntity<>(details, HttpStatus.BAD_GATEWAY);
    }
}
