package com.bolao.copa.controller;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, Object>> handleBusiness(RuntimeException exception) {
        return ResponseEntity.badRequest().body(error(friendly(exception.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        var message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(field -> friendly(field.getDefaultMessage()))
                .orElse("Verifique os campos informados.");
        return ResponseEntity.badRequest().body(error(message));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("E-mail ou senha inválidos."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("Você não tem permissão para realizar esta ação."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        LOGGER.error("Erro inesperado na API", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("Ocorreu um erro inesperado. Tente novamente em instantes."));
    }

    private Map<String, Object> error(String message) {
        return Map.of("timestamp", Instant.now(), "error", message);
    }

    private String friendly(String message) {
        if (message == null || message.isBlank()) {
            return "Ocorreu um erro inesperado. Tente novamente em instantes.";
        }
        var normalized = message.toLowerCase();
        if (normalized.contains("could not initialize proxy") || normalized.contains("no session")
                || normalized.contains("hibernate") || normalized.contains("internal server error")) {
            return "Não foi possível carregar os dados do bolão. Tente novamente.";
        }
        if ("não deve estar em branco".equals(normalized) || normalized.contains("must not be blank")) {
            return "Preencha os campos obrigatórios.";
        }
        return message;
    }
}
