package com.bolao.copa.arena.api;

import com.bolao.copa.arena.service.ArenaProblem;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.bolao.copa.arena")
public class ArenaExceptionHandler {
    @ExceptionHandler(ArenaProblem.NotFound.class)
    ResponseEntity<Map<String, Object>> notFound(ArenaProblem.NotFound exception) { return error(HttpStatus.NOT_FOUND, exception.getMessage()); }
    @ExceptionHandler(ArenaProblem.Conflict.class)
    ResponseEntity<Map<String, Object>> conflict(ArenaProblem.Conflict exception) { return error(HttpStatus.CONFLICT, exception.getMessage()); }
    @ExceptionHandler(ArenaProblem.RuleViolation.class)
    ResponseEntity<Map<String, Object>> rule(ArenaProblem.RuleViolation exception) { return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage()); }
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Map<String, Object>> concurrent() { return error(HttpStatus.CONFLICT, "Os dados foram atualizados por outra operação. Recarregue e tente novamente."); }
    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now(), "status", status.value(), "error", message));
    }
}
