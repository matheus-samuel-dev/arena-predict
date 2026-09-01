package com.bolao.copa.arena.api;

import com.bolao.copa.arena.domain.ArenaEnums.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class ExperienceDtos {
    private ExperienceDtos() { }

    public record ProfileResponse(Long userId, String name, String email, String role, Instant createdAt, String avatarUrl,
                                  String bio, List<String> favoriteSports, String theme, String language,
                                  boolean notifications, boolean publicProfile, int level, long xp, long points) { }
    public record ProfileUpdateRequest(@NotBlank @Size(min = 2, max = 100) String name,
                                       @NotBlank @Email @Size(max = 180) String email,
                                       @Size(max = 300) String avatarUrl, @Size(max = 500) String bio,
                                       @Size(max = 12) List<@Size(max = 40) String> favoriteSports,
                                       Boolean publicProfile) { }
    public record PasswordUpdateRequest(@NotBlank @Size(max = 72) String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) { }
    public record PreferenceUpdateRequest(@Pattern(regexp = "dark|light") String theme,
                                          @Pattern(regexp = "pt-BR|en-US") String language,
                                          Boolean notifications, Boolean publicProfile) { }
    public record PreferenceResponse(String theme, String language, boolean notifications, boolean publicProfile) { }

    public record AchievementResponse(Long id, String code, String name, String description, String rarity,
                                      int progress, int target, int pointsReward, boolean unlocked, Instant unlockedAt) { }
    public record ChallengeResponse(Long id, String code, String name, String description, int progress,
                                    int target, int rewardPoints, Instant expiresAt, boolean completed) { }

    public record CommunityAuthor(Long id, String name, String avatarUrl) { }
    public record PostResponse(Long id, CommunityAuthor author, String authorName, String avatarUrl,
                               String content, String topic, Instant createdAt, Instant updatedAt,
                               long likeCount, long commentCount, boolean likedByCurrentUser,
                               boolean ownedByCurrentUser) { }
    public record PostRequest(@NotBlank @Size(min = 3, max = 600) String content,
                              @Size(max = 80) String topic) { }
    public record CommentResponse(Long id, Long postId, CommunityAuthor author, String content, Instant createdAt,
                                  boolean ownedByCurrentUser) { }
    public record CommentRequest(@NotBlank @Size(min = 2, max = 600) String content) { }
    public record ReportRequest(@NotBlank @Size(min = 3, max = 300) String reason) { }
    public record ReportResponse(Long id, Long postId, String postExcerpt, Long reporterId, String reporterName,
                                 String reason, ReportStatus status, String moderatorNote,
                                 Instant createdAt, Instant reviewedAt) { }
    public record ReportModerationRequest(@NotNull ReportStatus status, @Size(max = 500) String moderatorNote,
                                          Boolean hidePost) { }
    public record PostModerationRequest(@NotNull ContentStatus status) { }
}
