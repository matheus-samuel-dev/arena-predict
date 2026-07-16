package com.bolao.copa.arena.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "arena_event_participants",
       uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "competitor_id"}))
public class EventParticipant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "event_id") private ArenaEvent event;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "competitor_id") private Competitor competitor;
    @Column(nullable = false) private int displayOrder;
    private Integer position;
    @Column(length = 80) private String scoreLabel;

    public Long getId() { return id; }
    public ArenaEvent getEvent() { return event; } public void setEvent(ArenaEvent event) { this.event = event; }
    public Competitor getCompetitor() { return competitor; } public void setCompetitor(Competitor competitor) { this.competitor = competitor; }
    public int getDisplayOrder() { return displayOrder; } public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public Integer getPosition() { return position; } public void setPosition(Integer position) { this.position = position; }
    public String getScoreLabel() { return scoreLabel; } public void setScoreLabel(String scoreLabel) { this.scoreLabel = scoreLabel; }
}
