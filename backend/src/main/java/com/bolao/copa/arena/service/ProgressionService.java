package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ExperienceDtos.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.entity.User;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressionService {
    private final AchievementDefinitionRepository achievementDefinitions;
    private final UserAchievementRepository userAchievements;
    private final ChallengeDefinitionRepository challengeDefinitions;
    private final UserChallengeRepository userChallenges;
    private final ArenaPredictionRepository predictions;
    private final ArenaPoolMemberRepository poolMembers;
    private final PointWalletService wallets;
    private final ArenaNotificationService notifications;

    public ProgressionService(AchievementDefinitionRepository achievementDefinitions,
                              UserAchievementRepository userAchievements,
                              ChallengeDefinitionRepository challengeDefinitions,
                              UserChallengeRepository userChallenges, ArenaPredictionRepository predictions,
                              ArenaPoolMemberRepository poolMembers, PointWalletService wallets,
                              ArenaNotificationService notifications) {
        this.achievementDefinitions = achievementDefinitions; this.userAchievements = userAchievements;
        this.challengeDefinitions = challengeDefinitions; this.userChallenges = userChallenges;
        this.predictions = predictions; this.poolMembers = poolMembers; this.wallets = wallets; this.notifications = notifications;
    }

    @Transactional
    public List<AchievementResponse> achievements(User user) {
        Metrics metrics = metrics(user, null, null);
        return achievementDefinitions.findByActiveTrueOrderByIdAsc().stream()
                .map(definition -> evaluateAchievement(user, definition, metrics)).toList();
    }

    @Transactional
    public List<ChallengeResponse> challenges(User user) {
        Instant now = Instant.now();
        return challengeDefinitions.findByActiveTrueOrderByExpiresAtAsc().stream()
                .filter(definition -> !now.isBefore(definition.getStartsAt()) && now.isBefore(definition.getExpiresAt()))
                .map(definition -> evaluateChallenge(user, definition,
                        metrics(user, definition.getStartsAt(), definition.getExpiresAt()))).toList();
    }

    private AchievementResponse evaluateAchievement(User user, AchievementDefinition definition, Metrics metrics) {
        int progress = achievementProgress(definition.getRule(), metrics);
        UserAchievement state = userAchievements.findByUserAndAchievement(user, definition).orElseGet(() -> {
            UserAchievement created = new UserAchievement(); created.setUser(user); created.setAchievement(definition); return created;
        });
        state.setProgress(progress);
        if (progress >= definition.getTarget() && state.getUnlockedAt() == null) {
            state.setUnlockedAt(Instant.now());
            wallets.apply(user, definition.getPointsReward(), PointTransactionType.ACHIEVEMENT,
                    "achievement:user:" + user.getId() + ":" + definition.getCode(), "ACHIEVEMENT", definition.getCode(),
                    "Conquista desbloqueada: " + definition.getName());
            state.setRewardGranted(true);
            notifications.create(user, NotificationType.ACHIEVEMENT_UNLOCKED, "Conquista desbloqueada",
                    definition.getName() + " · +" + definition.getPointsReward() + " pontos virtuais", "/achievements");
        }
        state = userAchievements.save(state);
        return new AchievementResponse(definition.getId(), definition.getCode(), definition.getName(), definition.getDescription(),
                definition.getRarity(), state.getProgress(), definition.getTarget(), definition.getPointsReward(),
                state.getUnlockedAt() != null, state.getUnlockedAt());
    }

    private ChallengeResponse evaluateChallenge(User user, ChallengeDefinition definition, Metrics metrics) {
        int progress = challengeProgress(definition.getMetric(), metrics);
        UserChallenge state = userChallenges.findByUserAndChallenge(user, definition).orElseGet(() -> {
            UserChallenge created = new UserChallenge(); created.setUser(user); created.setChallenge(definition); return created;
        });
        state.setProgress(progress);
        if (progress >= definition.getTarget() && state.getCompletedAt() == null) {
            state.setCompletedAt(Instant.now());
            wallets.apply(user, definition.getRewardPoints(), PointTransactionType.CHALLENGE_COMPLETED,
                    "challenge:user:" + user.getId() + ":" + definition.getCode(), "CHALLENGE", definition.getCode(),
                    "Desafio concluído: " + definition.getName());
            state.setRewardGranted(true);
            notifications.create(user, NotificationType.CHALLENGE_COMPLETED, "Desafio concluído",
                    definition.getName() + " · +" + definition.getRewardPoints() + " pontos virtuais", "/app");
        }
        state = userChallenges.save(state);
        return new ChallengeResponse(definition.getId(), definition.getCode(), definition.getName(), definition.getDescription(),
                state.getProgress(), definition.getTarget(), definition.getRewardPoints(), definition.getExpiresAt(), state.getCompletedAt() != null);
    }

    private Metrics metrics(User user, Instant startsAt, Instant expiresAt) {
        List<ArenaPrediction> values = predictions.findByUserOrderByPlacedAtDesc(user).stream()
                .filter(value -> value.getStatus() == PredictionStatus.ACTIVE
                        || value.getStatus() == PredictionStatus.WON || value.getStatus() == PredictionStatus.LOST)
                .filter(value -> startsAt == null || !value.getPlacedAt().isBefore(startsAt))
                .filter(value -> expiresAt == null || value.getPlacedAt().isBefore(expiresAt))
                .toList();
        int wins = (int) values.stream().filter(value -> value.getStatus() == PredictionStatus.WON).count();
        int sports = (int) values.stream().map(value -> value.getEvent().getChampionship().getSport().getId()).distinct().count();
        return new Metrics(values.size(), wins, sports, Math.toIntExact(poolMembers.countByUser(user)));
    }
    private int achievementProgress(AchievementRule rule, Metrics value) {
        return switch (rule) {
            case FIRST_PREDICTION, PREDICTION_COUNT -> value.predictions();
            case FIRST_WIN, WON_COUNT -> value.wins();
            case POOL_MEMBER -> value.poolMemberships();
        };
    }
    private int challengeProgress(ChallengeMetric metric, Metrics value) {
        return switch (metric) {
            case PREDICTION_COUNT -> value.predictions();
            case WON_COUNT -> value.wins();
            case SPORT_VARIETY -> value.sports();
            case POOL_MEMBER_COUNT -> value.poolMemberships();
        };
    }
    private record Metrics(int predictions, int wins, int sports, int poolMemberships) { }
}
