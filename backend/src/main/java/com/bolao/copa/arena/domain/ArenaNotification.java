package com.bolao.copa.arena.domain;

import com.bolao.copa.arena.domain.ArenaEnums.NotificationType;
import com.bolao.copa.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_notifications")
public class ArenaNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private NotificationType type;
    @Column(nullable = false, length = 140)
    private String title;
    @Column(nullable = false, length = 600)
    private String message;
    @Column(length = 240)
    private String targetUrl;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    private Instant readAt;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
}
