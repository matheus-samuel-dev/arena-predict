package com.bolao.copa.arena.domain;

import com.bolao.copa.arena.domain.ArenaEnums.ContentStatus;
import com.bolao.copa.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_community_comments")
public class CommunityComment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id") private CommunityPost post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id") private User author;
    @Column(nullable = false, length = 600) private String content;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ContentStatus status = ContentStatus.PUBLISHED;
    @Column(nullable = false) private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public CommunityPost getPost() { return post; } public void setPost(CommunityPost post) { this.post = post; }
    public User getAuthor() { return author; } public void setAuthor(User author) { this.author = author; }
    public String getContent() { return content; } public void setContent(String content) { this.content = content; }
    public ContentStatus getStatus() { return status; } public void setStatus(ContentStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
