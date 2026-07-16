package com.bolao.copa.arena.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "arena_market_options", uniqueConstraints = @UniqueConstraint(columnNames = {"market_id", "option_key"}))
public class MarketOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "market_id")
    private PredictionMarket market;
    @Column(name = "option_key", nullable = false, length = 80)
    private String key;
    @Column(nullable = false, length = 140)
    private String label;
    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal multiplier;
    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public PredictionMarket getMarket() { return market; }
    public void setMarket(PredictionMarket market) { this.market = market; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
