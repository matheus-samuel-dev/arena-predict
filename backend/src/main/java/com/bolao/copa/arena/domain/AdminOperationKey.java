package com.bolao.copa.arena.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Immutable receipt used to make sensitive administrative commands replay-safe. */
@Entity
@Table(name = "arena_admin_operation_keys", uniqueConstraints =
        @UniqueConstraint(name = "uk_arena_admin_operation_key", columnNames = {"operation_type", "idempotency_key"}))
public class AdminOperationKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_type", nullable = false, length = 40, updatable = false)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, length = 80, updatable = false)
    private String idempotencyKey;

    @Column(name = "resource_id", nullable = false, length = 80, updatable = false)
    private String resourceId;

    @Column(name = "request_fingerprint", nullable = false, length = 200, updatable = false)
    private String requestFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminOperationKey() {
    }

    public AdminOperationKey(String operation, String idempotencyKey, String resourceId,
                             String requestFingerprint, Instant createdAt) {
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.resourceId = resourceId;
        this.requestFingerprint = requestFingerprint;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getOperation() { return operation; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getResourceId() { return resourceId; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public Instant getCreatedAt() { return createdAt; }
}
