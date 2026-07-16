package com.bolao.copa.arena.domain;

import com.bolao.copa.arena.domain.ArenaEnums.PredictionStatus;
import com.bolao.copa.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "arena_predictions")
public class ArenaPrediction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "event_id")
    private ArenaEvent event;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "market_id")
    private PredictionMarket market;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "option_id")
    private MarketOption option;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "pool_id")
    private ArenaPool pool;
    @Column(nullable = false)
    private int stakePoints;
    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal multiplier;
    @Column(nullable = false)
    private int potentialPoints;
    @Column(nullable = false)
    private int rewardedPoints;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PredictionStatus status = PredictionStatus.ACTIVE;
    @Column(nullable = false, unique = true, length = 120)
    private String idempotencyKey;
    @Column(nullable = false)
    private Instant placedAt = Instant.now();
    private Instant resolvedAt;
    @Version
    private long version;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public ArenaEvent getEvent() { return event; }
    public void setEvent(ArenaEvent event) { this.event = event; }
    public PredictionMarket getMarket() { return market; }
    public void setMarket(PredictionMarket market) { this.market = market; }
    public MarketOption getOption() { return option; }
    public void setOption(MarketOption option) { this.option = option; }
    public ArenaPool getPool() { return pool; }
    public void setPool(ArenaPool pool) { this.pool = pool; }
    public int getStakePoints() { return stakePoints; }
    public void setStakePoints(int stakePoints) { this.stakePoints = stakePoints; }
    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }
    public int getPotentialPoints() { return potentialPoints; }
    public void setPotentialPoints(int potentialPoints) { this.potentialPoints = potentialPoints; }
    public int getRewardedPoints() { return rewardedPoints; }
    public void setRewardedPoints(int rewardedPoints) { this.rewardedPoints = rewardedPoints; }
    public PredictionStatus getStatus() { return status; }
    public void setStatus(PredictionStatus status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getPlacedAt() { return placedAt; }
    public void setPlacedAt(Instant placedAt) { this.placedAt = placedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
