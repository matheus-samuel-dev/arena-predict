package com.bolao.copa.arena.domain;

import com.bolao.copa.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_user_challenges", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "challenge_id"}))
public class UserChallenge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "challenge_id") private ChallengeDefinition challenge;
    @Column(nullable = false) private int progress;
    private Instant windowStart;
    private Instant windowEnd;
    private Instant completedAt;
    @Column(nullable = false) private boolean rewardGranted;

    public Long getId() { return id; }
    public User getUser() { return user; } public void setUser(User user) { this.user = user; }
    public ChallengeDefinition getChallenge() { return challenge; } public void setChallenge(ChallengeDefinition challenge) { this.challenge = challenge; }
    public int getProgress() { return progress; } public void setProgress(int progress) { this.progress = progress; }
    public Instant getWindowStart() { return windowStart; } public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public Instant getWindowEnd() { return windowEnd; } public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public boolean isRewardGranted() { return rewardGranted; } public void setRewardGranted(boolean rewardGranted) { this.rewardGranted = rewardGranted; }
}
