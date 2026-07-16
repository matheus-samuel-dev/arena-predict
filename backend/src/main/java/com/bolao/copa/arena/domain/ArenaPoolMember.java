package com.bolao.copa.arena.domain;

import com.bolao.copa.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_pool_members", uniqueConstraints = @UniqueConstraint(columnNames = {"pool_id", "user_id"}))
public class ArenaPoolMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "pool_id")
    private ArenaPool pool;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false)
    private boolean moderator;
    @Column(nullable = false)
    private Instant joinedAt = Instant.now();

    public Long getId() { return id; }
    public ArenaPool getPool() { return pool; }
    public void setPool(ArenaPool pool) { this.pool = pool; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public boolean isModerator() { return moderator; }
    public void setModerator(boolean moderator) { this.moderator = moderator; }
    public Instant getJoinedAt() { return joinedAt; }
}
