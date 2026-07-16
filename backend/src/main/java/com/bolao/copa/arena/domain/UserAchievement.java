package com.bolao.copa.arena.domain;

import com.bolao.copa.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_user_achievements", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "achievement_id"}))
public class UserAchievement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "achievement_id") private AchievementDefinition achievement;
    @Column(nullable = false) private int progress;
    private Instant unlockedAt;
    @Column(nullable = false) private boolean rewardGranted;

    public Long getId() { return id; }
    public User getUser() { return user; } public void setUser(User user) { this.user = user; }
    public AchievementDefinition getAchievement() { return achievement; } public void setAchievement(AchievementDefinition achievement) { this.achievement = achievement; }
    public int getProgress() { return progress; } public void setProgress(int progress) { this.progress = progress; }
    public Instant getUnlockedAt() { return unlockedAt; } public void setUnlockedAt(Instant unlockedAt) { this.unlockedAt = unlockedAt; }
    public boolean isRewardGranted() { return rewardGranted; } public void setRewardGranted(boolean rewardGranted) { this.rewardGranted = rewardGranted; }
}
