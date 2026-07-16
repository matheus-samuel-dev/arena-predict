package com.bolao.copa.arena.domain;

import com.bolao.copa.arena.domain.ArenaEnums.ContentStatus;
import com.bolao.copa.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_community_posts")
public class CommunityPost {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id") private User author;
    @Column(nullable = false, length = 600) private String content;
    @Column(nullable = false, length = 80) private String topic = "Discussão geral";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ContentStatus status = ContentStatus.PUBLISHED;
    @Column(unique = true, length = 80) private String sourceKey;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public User getAuthor() { return author; } public void setAuthor(User author) { this.author = author; }
    public String getContent() { return content; } public void setContent(String content) { this.content = content; }
    public String getTopic() { return topic; } public void setTopic(String topic) { this.topic = topic; }
    public ContentStatus getStatus() { return status; } public void setStatus(ContentStatus status) { this.status = status; }
    public String getSourceKey() { return sourceKey; } public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; } public void touch() { updatedAt = Instant.now(); }
}
