package com.bolao.copa.controller;

import com.bolao.copa.config.CorrelationIdContext;
import java.time.Instant;
import java.util.LinkedHashMap;
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
        var fieldErrors = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(field ->
                fieldErrors.putIfAbsent(field.getField(), friendly(field.getDefaultMessage())));
        var message = fieldErrors.values().stream()
                .findFirst()
                .orElse("Verifique os campos informados.");
        var body = error(message);
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
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
        var body = new LinkedHashMap<String, Object>();
        body.put("timestamp", Instant.now());
        body.put("error", message);
        if (CorrelationIdContext.get() != null) body.put("correlationId", CorrelationIdContext.get());
        return body;
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
            return "Preencha este campo obrigatório.";
        }
        if (normalized.contains("must not be null") || normalized.contains("não deve ser nulo")) {
            return "Este campo é obrigatório.";
        }
        if (normalized.contains("must be a well-formed email") || normalized.contains("deve ser um endereço de e-mail")) {
            return "Informe um e-mail válido.";
        }
        if (normalized.contains("size must be between") || normalized.contains("tamanho deve estar entre")) {
            return "Revise o tamanho informado para este campo.";
        }
        if (normalized.contains("must be greater than or equal") || normalized.contains("deve ser maior ou igual")
                || normalized.contains("must be greater than") || normalized.contains("deve ser maior que")) {
            return "Informe um valor acima do limite mínimo permitido.";
        }
        if (normalized.contains("must be less than or equal") || normalized.contains("deve ser menor ou igual")
                || normalized.contains("must be less than") || normalized.contains("deve ser menor que")) {
            return "Informe um valor abaixo do limite máximo permitido.";
        }
        if (normalized.contains("must be positive") || normalized.contains("deve ser positivo")) {
            return "Informe um valor positivo.";
        }
        if (normalized.contains("must match") || normalized.contains("deve corresponder")) {
            return "Informe um valor no formato esperado.";
        }
        return message;
    }
}
