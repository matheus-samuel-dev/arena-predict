package com.bolao.copa.arena.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Immutable snapshot of a completed administrative or system operation. */
@Entity
@Table(name = "arena_admin_audit_events")
public class AdminAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long actorId;

    @Column(nullable = false, length = 120, updatable = false)
    private String actorName;

    @Column(nullable = false, length = 40, updatable = false)
    private String actorRole;

    @Column(nullable = false, length = 80, updatable = false)
    private String action;

    @Column(nullable = false, length = 60, updatable = false)
    private String resourceType;

    @Column(length = 80, updatable = false)
    private String resourceId;

    @Column(nullable = false, length = 20, updatable = false)
    private String status;

    @Column(length = 500, updatable = false)
    private String summary;

    @Column(length = 80, updatable = false)
    private String correlationId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAuditEvent() {
    }

    public AdminAuditEvent(Long actorId, String actorName, String actorRole, String action,
                           String resourceType, String resourceId, String status, String summary,
                           String correlationId, Instant createdAt) {
        this.actorId = actorId;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.status = status;
        this.summary = summary;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getActorRole() { return actorRole; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getStatus() { return status; }
    public String getSummary() { return summary; }
    public String getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }
}
