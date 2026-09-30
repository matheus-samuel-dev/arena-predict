package com.bolao.copa.arena.service.provider;

import java.time.Instant;

/** Safe diagnostics: never carry remote bodies, request headers, tokens or raw URLs. */
public class SportsProviderException extends RuntimeException {
    public enum Reason { NOT_CONFIGURED, AUTHENTICATION, RATE_LIMITED, UNAVAILABLE, TIMEOUT, INVALID_RESPONSE, NOT_FOUND }
    private final Reason reason;
    private final Instant retryAt;

    public SportsProviderException(Reason reason, String message, Instant retryAt) {
        super(message);
        this.reason = reason;
        this.retryAt = retryAt;
    }

    public Reason getReason() { return reason; }
    public Instant getRetryAt() { return retryAt; }
}
