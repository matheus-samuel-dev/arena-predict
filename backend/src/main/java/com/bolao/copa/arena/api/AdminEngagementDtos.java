package com.bolao.copa.arena.api;

import com.bolao.copa.arena.domain.ArenaEnums.*;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class AdminEngagementDtos {
    private AdminEngagementDtos() { }

    public record AchievementDefinitionResponse(Long id, String code, String name, String description,
                                                 String rarity, AchievementRule rule, int target,
                                                 int pointsReward, boolean active) { }
    public record AchievementDefinitionRequest(@NotBlank @Size(max = 60) String code,
                                                @NotBlank @Size(max = 120) String name,
                                                @NotBlank @Size(max = 500) String description,
                                                @NotBlank @Size(max = 30) String rarity,
                                                @NotNull AchievementRule rule,
                                                @Min(1) int target, @PositiveOrZero int pointsReward,
                                                Boolean active) { }

    public record ChallengeDefinitionResponse(Long id, String code, String name, String description,
                                               ChallengeMetric metric, int target, int rewardPoints,
                                               Instant startsAt, Instant expiresAt, boolean active) { }
    public record ChallengeDefinitionRequest(@NotBlank @Size(max = 60) String code,
                                              @NotBlank @Size(max = 120) String name,
                                              @NotBlank @Size(max = 500) String description,
                                              @NotNull ChallengeMetric metric,
                                              @Min(1) int target, @PositiveOrZero int rewardPoints,
                                              @NotNull Instant startsAt, @NotNull Instant expiresAt,
                                              Boolean active) { }

    public record AdminNotificationResponse(Long id, Long userId, String userName, NotificationType type,
                                            String title, String message, String targetUrl,
                                            Instant createdAt, boolean read) { }
    public record AdminNotificationRequest(Long userId, @NotNull NotificationType type,
                                           @NotBlank @Size(max = 140) String title,
                                           @NotBlank @Size(max = 600) String message,
                                           @Size(max = 240) String targetUrl) { }
    public record NotificationDispatchResponse(int recipients, String scope) { }
}
