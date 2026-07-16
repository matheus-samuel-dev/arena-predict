package com.bolao.copa.arena.domain;

import com.bolao.copa.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_community_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
public class CommunityLike {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id") private CommunityPost post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    public Long getId() { return id; }
    public CommunityPost getPost() { return post; } public void setPost(CommunityPost post) { this.post = post; }
    public User getUser() { return user; } public void setUser(User user) { this.user = user; }
    public Instant getCreatedAt() { return createdAt; }
}
