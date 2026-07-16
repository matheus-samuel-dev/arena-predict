package com.bolao.copa.arena.domain;

/** Shared vocabulary for the configurable ArenaPredict domain. */
public final class ArenaEnums {
    private ArenaEnums() {
    }

    public enum SportCategory { TRADITIONAL, ESPORTS, MOTORSPORT }
    public enum ChampionshipStatus { DRAFT, ACTIVE, FINISHED, ARCHIVED }
    public enum EventStatus { SCHEDULED, OPEN_FOR_PREDICTIONS, LIVE, FINISHED, CANCELLED, POSTPONED }
    public enum EventFormat { STANDARD, INDIVIDUAL, RACE, BO1, BO3, BO5 }
    public enum MarketStatus { DRAFT, OPEN, SUSPENDED, CLOSED, SETTLED, CANCELLED }
    public enum PredictionStatus { PENDING, ACTIVE, WON, LOST, CANCELLED, REFUNDED }
    public enum PointTransactionType {
        INITIAL_BONUS, PREDICTION_PLACED, PREDICTION_WON, REFUND, CHALLENGE_COMPLETED,
        ACHIEVEMENT, RANKING_REWARD, ADMIN_ADJUSTMENT
    }
    public enum PoolType { POOL, LEAGUE }
    public enum PoolStatus { DRAFT, OPEN, IN_PROGRESS, FINISHED, CANCELLED }
    public enum RankingPeriod { WEEKLY, MONTHLY, ALL }
    public enum RankingScope { GLOBAL, FRIENDS }
    public enum NotificationType {
        EVENT_STARTED, EVENT_FINISHED, RESULT_PROCESSED, PREDICTION_WON, CHALLENGE_COMPLETED,
        ACHIEVEMENT_UNLOCKED, RANKING_CHANGED, POOL_INVITE, COMMENT, REPLY, ADMIN_NOTICE, REFUND
    }
    public enum AchievementRule { FIRST_PREDICTION, FIRST_WIN, PREDICTION_COUNT, WON_COUNT, POOL_MEMBER }
    public enum ChallengeMetric { PREDICTION_COUNT, WON_COUNT, SPORT_VARIETY, POOL_MEMBER_COUNT }
    public enum ContentStatus { PUBLISHED, HIDDEN, REMOVED }
    public enum ReportStatus { PENDING, REVIEWED, DISMISSED, ACTIONED }
}
