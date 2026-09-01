package com.bolao.copa.arena.api;

import com.bolao.copa.arena.domain.ArenaEnums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ArenaDtos {
    private ArenaDtos() { }

    public static final String VIRTUAL_POINTS_NOTICE = "Pontos virtuais de entretenimento, sem valor financeiro e sem possibilidade de saque.";
    public static final int MAX_PREDICTION_STAKE_POINTS = 20_000;
    public static final int MAX_CLIENT_IDEMPOTENCY_KEY_LENGTH = 80;

    public record SportResponse(Long id, String code, String name, SportCategory category, String icon,
                                boolean active, int displayOrder) { }
    public record ChampionshipResponse(Long id, Long sportId, String sportCode, String name, String slug,
                                       String season, ChampionshipStatus status, String imageUrl,
                                       Instant startsAt, Instant endsAt) { }
    public record CompetitorResponse(Long id, Long sportId, String sportCode, String sportName,
                                     String name, String code, String imageUrl, String country,
                                     boolean active) { }
    public record CompetitorSummary(Long id, String name, String code, String imageUrl) { }
    public record EventParticipantResponse(Long id, CompetitorSummary competitor, int displayOrder,
                                           Integer position, String scoreLabel) { }
    public record MarketOptionResponse(Long id, String key, String label, BigDecimal multiplier, boolean active) { }
    public record MarketResponse(Long id, String code, String name, MarketStatus status, int minimumPoints,
                                 String resultOptionKey, List<MarketOptionResponse> options) { }
    public record EventResponse(Long id, String externalKey, Long championshipId, String championship,
                                SportResponse sport, String title, String stage, String venue, String broadcast,
                                String imageUrl, CompetitorSummary homeCompetitor, CompetitorSummary awayCompetitor,
                                Instant startsAt, Instant predictionClosesAt, EventStatus status, EventFormat format,
                                int bestOf, Integer homeScore, Integer awayScore, String clock, String period,
                                String liveData, boolean featured, boolean demo,
                                List<EventParticipantResponse> participants, List<MarketResponse> markets) { }

    public record SportRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 100) String name,
                               @NotNull SportCategory category, @Size(max = 80) String icon,
                               Boolean active, Integer displayOrder) { }
    public record ChampionshipRequest(@NotNull Long sportId, @NotBlank @Size(max = 120) String name,
                                      @NotBlank @Size(max = 120) String slug, @NotBlank @Size(max = 40) String season,
                                      ChampionshipStatus status, @Size(max = 300) String imageUrl,
                                      Instant startsAt, Instant endsAt) { }
    public record CompetitorRequest(@NotNull Long sportId, @NotBlank @Size(max = 120) String name,
                                    @NotBlank @Size(max = 30) String code, @Size(max = 300) String imageUrl,
                                    @Size(max = 80) String country, Boolean active) { }
    public record EventRequest(@NotBlank @Size(max = 100) String externalKey, @NotNull Long championshipId,
                               Long homeCompetitorId, Long awayCompetitorId, @NotBlank @Size(max = 180) String title,
                               @Size(max = 100) String stage, @Size(max = 160) String venue,
                               @Size(max = 160) String broadcast, @Size(max = 300) String imageUrl,
                               @NotNull Instant startsAt, @NotNull Instant predictionClosesAt,
                               EventStatus status, EventFormat format, @Min(1) @Max(5) Integer bestOf,
                               Boolean featured, Boolean demo,
                               List<@Valid EventParticipantRequest> participants) { }
    public record EventParticipantRequest(@NotNull Long competitorId, @PositiveOrZero Integer displayOrder,
                                          @Positive Integer position, @Size(max = 80) String scoreLabel) { }
    public record MarketOptionRequest(@NotBlank @Size(max = 80) String key,
                                      @NotBlank @Size(max = 140) String label,
                                      @NotNull @DecimalMin("1.001") @Digits(integer = 5, fraction = 3) BigDecimal multiplier,
                                      Boolean active) { }
    public record MarketRequest(@NotNull Long eventId, @NotBlank @Size(max = 80) String code,
                                @NotBlank @Size(max = 140) String name, MarketStatus status,
                                @Min(1) Integer minimumPoints, @NotEmpty List<@NotNull MarketOptionRequest> options) { }
    public record MarketStatusRequest(@NotNull MarketStatus status) { }
    public record SettleMarketRequest(@NotBlank @Size(max = 80) String correctOptionKey) { }
    public record EventResultRequest(@NotNull @PositiveOrZero Integer homeScore,
                                     @NotNull @PositiveOrZero Integer awayScore, Boolean finishEvent) { }
    public record EventClassificationRequest(@NotEmpty List<@Valid EventParticipantRequest> participants,
                                             Boolean finishEvent) { }

    public record PlacePredictionRequest(@NotNull Long eventId, @NotNull Long marketId, @NotNull Long optionId,
                                         @NotNull @Min(1) @Max(MAX_PREDICTION_STAKE_POINTS) Integer stakePoints,
                                         Long poolId,
                                         @Size(max = MAX_CLIENT_IDEMPOTENCY_KEY_LENGTH) String idempotencyKey) { }
    public record PredictionResponse(Long id, Long eventId, String eventTitle, Long marketId, String marketName,
                                     Long optionId, String optionLabel, int stakePoints, BigDecimal multiplier,
                                     int potentialPoints, int rewardedPoints, PredictionStatus status, Long poolId,
                                     Instant placedAt, Instant resolvedAt, boolean canCancel) { }
    public record WalletResponse(long balance, long lifetimeEarned, long lifetimeUsed, Instant updatedAt,
                                 String virtualPointsNotice) { }
    public record PointTransactionResponse(Long id, PointTransactionType type, long amount, long balanceAfter,
                                           String description, String referenceType, String referenceId,
                                           Instant createdAt) { }

    public record PoolRequest(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description,
                              Long sportId, Long championshipId, Boolean publicPool,
                              @Min(2) @Max(500) Integer maxParticipants, @PositiveOrZero Integer virtualPrizePoints,
                              @NotBlank @Size(max = 1200) String rules, Instant startsAt, Instant endsAt,
                              PoolType poolType, Boolean recurring) {
        public PoolRequest(String name, String description, Long sportId, Long championshipId,
                           Boolean publicPool, Integer maxParticipants, Integer virtualPrizePoints,
                           String rules, Instant startsAt, Instant endsAt) {
            this(name, description, sportId, championshipId, publicPool, maxParticipants,
                    virtualPrizePoints, rules, startsAt, endsAt, PoolType.POOL, false);
        }
    }
    public record JoinPoolRequest(@NotBlank @Size(max = 16) String inviteCode) { }
    public record PoolResponse(Long id, String name, String description, SportResponse sport,
                               ChampionshipResponse championship, String ownerName, String inviteCode,
                               boolean publicPool, int maxParticipants, int participantCount,
                               int virtualPrizePoints, String rules, PoolStatus status,
                               Instant startsAt, Instant endsAt, boolean joined, boolean owner,
                               PoolType poolType, boolean recurring) { }
    public record RankingRow(int position, Long userId, String playerName, long points, long correctPredictions,
                             long totalPredictions, double accuracy, long streak, boolean currentUser) { }
    public record NotificationResponse(Long id, NotificationType type, String title, String message,
                                       String targetUrl, Instant createdAt, boolean read) { }
    public record DashboardResponse(String playerName, int level, String levelTitle, long xp, long nextLevelXp,
                                    long points, Integer rankingPosition,
                                    long activePredictions, long finishedPredictions, long wonPredictions,
                                    double accuracy, int streak, long unreadNotifications,
                                    List<EventResponse> featuredEvents, List<EventResponse> liveEvents,
                                    List<EventResponse> upcomingEvents, List<PredictionResponse> recentPredictions,
                                    List<PoolResponse> activePools, List<RankingRow> weeklyRanking,
                                    String virtualPointsNotice, boolean demoMode,
                                    List<ExperienceDtos.ChallengeResponse> challenges,
                                    List<ExperienceDtos.AchievementResponse> recentAchievements,
                                    List<PerformancePoint> performance) { }
    public record PerformancePoint(String label, Instant date, double accuracy) { }
    public record AdminDashboardResponse(long users, long liveEvents, long upcomingEvents, long activePredictions,
                                         long openMarkets, long activePools, long pointsMoved,
                                         long eventsAwaitingResult, boolean demoMode) { }
    public record SettlementResponse(Long marketId, String correctOptionKey, int winners, int losers,
                                     long rewardedPoints, boolean alreadySettled) { }
}
