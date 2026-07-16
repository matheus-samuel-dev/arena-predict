package com.bolao.copa.arena.domain;

import com.bolao.copa.arena.domain.ArenaEnums.ChallengeMetric;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_challenge_definitions")
public class ChallengeDefinition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 60) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 500) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ChallengeMetric metric;
    @Column(nullable = false) private int target;
    @Column(nullable = false) private int rewardPoints;
    @Column(nullable = false) private Instant startsAt;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private boolean active = true;

    public Long getId() { return id; }
    public String getCode() { return code; } public void setCode(String code) { this.code = code; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public ChallengeMetric getMetric() { return metric; } public void setMetric(ChallengeMetric metric) { this.metric = metric; }
    public int getTarget() { return target; } public void setTarget(int target) { this.target = target; }
    public int getRewardPoints() { return rewardPoints; } public void setRewardPoints(int rewardPoints) { this.rewardPoints = rewardPoints; }
    public Instant getStartsAt() { return startsAt; } public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getExpiresAt() { return expiresAt; } public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isActive() { return active; } public void setActive(boolean active) { this.active = active; }
}
