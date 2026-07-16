package com.bolao.copa.arena.domain;

import com.bolao.copa.arena.domain.ArenaEnums.MarketStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_markets", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "code"}))
public class PredictionMarket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "event_id")
    private ArenaEvent event;
    @Column(nullable = false, length = 80)
    private String code;
    @Column(nullable = false, length = 140)
    private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private MarketStatus status = MarketStatus.DRAFT;
    @Column(nullable = false)
    private int minimumPoints = 10;
    @Column(length = 80)
    private String resultOptionKey;
    private Instant settledAt;
    @Version
    private long version;

    public Long getId() { return id; }
    public ArenaEvent getEvent() { return event; }
    public void setEvent(ArenaEvent event) { this.event = event; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public MarketStatus getStatus() { return status; }
    public void setStatus(MarketStatus status) { this.status = status; }
    public int getMinimumPoints() { return minimumPoints; }
    public void setMinimumPoints(int minimumPoints) { this.minimumPoints = minimumPoints; }
    public String getResultOptionKey() { return resultOptionKey; }
    public void setResultOptionKey(String resultOptionKey) { this.resultOptionKey = resultOptionKey; }
    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }
}
