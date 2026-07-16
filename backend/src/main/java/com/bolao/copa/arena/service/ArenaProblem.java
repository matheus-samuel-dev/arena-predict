package com.bolao.copa.arena.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public final class ArenaProblem {
    private ArenaProblem() { }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class Conflict extends RuntimeException {
        public Conflict(String message) { super(message); }
    }

    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public static class RuleViolation extends RuntimeException {
        public RuleViolation(String message) { super(message); }
    }
}
