package com.bolao.copa.arena.domain;

import com.bolao.copa.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_point_wallets")
public class PointWallet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", unique = true)
    private User user;
    @Column(nullable = false)
    private long balance;
    @Column(nullable = false)
    private long lifetimeEarned;
    @Column(nullable = false)
    private long lifetimeUsed;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public long getBalance() { return balance; }
    public void setBalance(long balance) { this.balance = balance; }
    public long getLifetimeEarned() { return lifetimeEarned; }
    public void setLifetimeEarned(long lifetimeEarned) { this.lifetimeEarned = lifetimeEarned; }
    public long getLifetimeUsed() { return lifetimeUsed; }
    public void setLifetimeUsed(long lifetimeUsed) { this.lifetimeUsed = lifetimeUsed; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = Instant.now(); }
}
