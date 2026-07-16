package com.bolao.copa.arena.domain;

import com.bolao.copa.arena.domain.ArenaEnums.AchievementRule;
import jakarta.persistence.*;

@Entity
@Table(name = "arena_achievement_definitions")
public class AchievementDefinition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 60) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 500) private String description;
    @Column(nullable = false, length = 30) private String rarity = "COMMON";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private AchievementRule rule;
    @Column(nullable = false) private int target;
    @Column(nullable = false) private int pointsReward;
    @Column(nullable = false) private boolean active = true;

    public Long getId() { return id; }
    public String getCode() { return code; } public void setCode(String code) { this.code = code; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public String getRarity() { return rarity; } public void setRarity(String rarity) { this.rarity = rarity; }
    public AchievementRule getRule() { return rule; } public void setRule(AchievementRule rule) { this.rule = rule; }
    public int getTarget() { return target; } public void setTarget(int target) { this.target = target; }
    public int getPointsReward() { return pointsReward; } public void setPointsReward(int pointsReward) { this.pointsReward = pointsReward; }
    public boolean isActive() { return active; } public void setActive(boolean active) { this.active = active; }
}
