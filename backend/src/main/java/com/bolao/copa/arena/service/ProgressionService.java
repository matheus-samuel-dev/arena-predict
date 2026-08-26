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
        this.predictions = predictions; this.poolMembers = poolMembers; this.wallets = wallets;
        this.notifications = notifications;
    }

    /**
     * Returns the current achievement projection without creating state,
     * granting points or emitting notifications. HTTP GET handlers use this
     * method, so refreshing a page can never change a participant's balance.
     */
    @Transactional(readOnly = true)
    public List<AchievementResponse> achievements(User user) {
        Metrics metrics = metrics(user, null, null);
        Map<Long, UserAchievement> states = userAchievements.findByUserOrderByUnlockedAtDesc(user).stream()
                .collect(java.util.stream.Collectors.toMap(
                        state -> state.getAchievement().getId(), state -> state));
        return achievementDefinitions.findByActiveTrueOrderByIdAsc().stream()
                .map(definition -> achievementResponse(definition, metrics, states.get(definition.getId())))
                .toList();
    }

    /**
     * Returns the current challenge projection without persisting progress or
     * granting rewards. Progress is evaluated on commands through refresh().
     */
    @Transactional(readOnly = true)
    public List<ChallengeResponse> challenges(User user) {
        Instant now = Instant.now();
        Map<Long, UserChallenge> states = userChallenges.findByUser(user).stream()
                .collect(java.util.stream.Collectors.toMap(
                        state -> state.getChallenge().getId(), state -> state));
        return challengeDefinitions.findByActiveTrueOrderByExpiresAtAsc().stream()
                .filter(definition -> !now.isBefore(definition.getStartsAt()) && now.isBefore(definition.getExpiresAt()))
                .map(definition -> challengeResponse(definition,
                        metrics(user, definition.getStartsAt(), definition.getExpiresAt()),
                        states.get(definition.getId())))
                .toList();
    }

    /**
     * Reconciles progression after a domain command changed one of its source
     * metrics. Ledger idempotency keys and the unique user/definition state
     * constraints make repeated command retries safe.
     */
    @Transactional
    public void refresh(User user) {
        // The wallet is the canonical per-participant mutex used by points and
        // progression commands. This serializes state upserts and rewards while
        // preserving one consistent lock order across every caller.
        wallets.lockParticipant(user);
        Metrics achievementMetrics = metrics(user, null, null);
        achievementDefinitions.findByActiveTrueOrderByIdAsc()
                .forEach(definition -> evaluateAchievement(user, definition, achievementMetrics));

        Instant now = Instant.now();
        challengeDefinitions.findByActiveTrueOrderByExpiresAtAsc().stream()
                .filter(definition -> !now.isBefore(definition.getStartsAt()) && now.isBefore(definition.getExpiresAt()))
                .forEach(definition -> evaluateChallenge(user, definition,
                        metrics(user, definition.getStartsAt(), definition.getExpiresAt())));
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
        return achievementResponse(definition, metrics, state);
    }

    private ChallengeResponse evaluateChallenge(User user, ChallengeDefinition definition, Metrics metrics) {
        int progress = challengeProgress(definition.getMetric(), metrics);
        UserChallenge state = userChallenges.findByUserAndChallenge(user, definition).orElseGet(() -> {
            UserChallenge created = new UserChallenge(); created.setUser(user); created.setChallenge(definition); return created;
        });
        if (!sameChallengeWindow(state, definition)) {
            state.setWindowStart(definition.getStartsAt());
            state.setWindowEnd(definition.getExpiresAt());
            state.setProgress(0);
            state.setCompletedAt(null);
            state.setRewardGranted(false);
        }
        state.setProgress(progress);
        if (progress >= definition.getTarget() && state.getCompletedAt() == null) {
            state.setCompletedAt(Instant.now());
            wallets.apply(user, definition.getRewardPoints(), PointTransactionType.CHALLENGE_COMPLETED,
                    "challenge:user:" + user.getId() + ":" + definition.getCode() + ":"
                            + definition.getStartsAt().toEpochMilli(),
                    "CHALLENGE", definition.getCode(),
                    "Desafio concluído: " + definition.getName());
            state.setRewardGranted(true);
            notifications.create(user, NotificationType.CHALLENGE_COMPLETED, "Desafio concluído",
                    definition.getName() + " · +" + definition.getRewardPoints() + " pontos virtuais", "/app");
        }
        state = userChallenges.save(state);
        return challengeResponse(definition, metrics, state);
    }

    private AchievementResponse achievementResponse(AchievementDefinition definition, Metrics metrics,
                                                      UserAchievement state) {
        int progress = achievementProgress(definition.getRule(), metrics);
        Instant unlockedAt = state == null ? null : state.getUnlockedAt();
        return new AchievementResponse(definition.getId(), definition.getCode(), definition.getName(),
                definition.getDescription(), definition.getRarity(), progress, definition.getTarget(),
                definition.getPointsReward(), unlockedAt != null, unlockedAt);
    }

    private ChallengeResponse challengeResponse(ChallengeDefinition definition, Metrics metrics,
                                                UserChallenge state) {
        int progress = challengeProgress(definition.getMetric(), metrics);
        boolean completedInCurrentWindow = state != null
                && sameChallengeWindow(state, definition)
                && state.getCompletedAt() != null;
        return new ChallengeResponse(definition.getId(), definition.getCode(), definition.getName(),
                definition.getDescription(), progress, definition.getTarget(), definition.getRewardPoints(),
                definition.getExpiresAt(), completedInCurrentWindow);
    }

    private boolean sameChallengeWindow(UserChallenge state, ChallengeDefinition definition) {
        return Objects.equals(state.getWindowStart(), definition.getStartsAt())
                && Objects.equals(state.getWindowEnd(), definition.getExpiresAt());
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
