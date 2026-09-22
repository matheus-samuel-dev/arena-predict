package com.bolao.copa.arena.domain;

import com.bolao.copa.arena.domain.ArenaEnums.MarketStatus;
import com.bolao.copa.arena.domain.ArenaEnums.MarketTimingMode;
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
    @Column(length = 80)
    private String templateCode;
    @Column(length = 24000)
    private String definitionData;
    @Column(nullable = false, length = 80)
    private String category = "Principais";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private MarketTimingMode timingMode = MarketTimingMode.PRE_MATCH_ONLY;
    private Instant opensAt;
    private Instant closesAt;
    @Column(length = 500)
    private String statusReason;
    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String value) { statusReason=value; }
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private MarketStatus status = MarketStatus.DRAFT;
    @Column(nullable = false)
    private int minimumPoints = 10;
    @Column(length = 1000)
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
    public String getTemplateCode() { return templateCode; }
    public String getDefinitionData() { return definitionData; }
    public void setDefinitionData(String value) { definitionData = value; }
    public void setTemplateCode(String value) { templateCode = value; }
    public String getCategory() { return category; }
    public void setCategory(String value) { category = value; }
    public MarketTimingMode getTimingMode() { return timingMode; }
    public void setTimingMode(MarketTimingMode value) { timingMode = value; }
    public Instant getOpensAt() { return opensAt; }
    public void setOpensAt(Instant value) { opensAt = value; }
    public Instant getClosesAt() { return closesAt; }
    public void setClosesAt(Instant value) { closesAt = value; }
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
