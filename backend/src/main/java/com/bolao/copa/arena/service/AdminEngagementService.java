package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.AdminEngagementDtos.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminEngagementService {
    private final AchievementDefinitionRepository achievements;
    private final ChallengeDefinitionRepository challenges;
    private final ArenaNotificationRepository notifications;
    private final ArenaNotificationService notificationService;
    private final UserRepository users;

    public AdminEngagementService(AchievementDefinitionRepository achievements,
                                  ChallengeDefinitionRepository challenges,
                                  ArenaNotificationRepository notifications,
                                  ArenaNotificationService notificationService,
                                  UserRepository users) {
        this.achievements = achievements; this.challenges = challenges; this.notifications = notifications;
        this.notificationService = notificationService; this.users = users;
    }

    @Transactional(readOnly = true)
    public List<AchievementDefinitionResponse> achievements() {
        return achievements.findAll(Sort.by("id")).stream().map(this::achievement).toList();
    }

    @Transactional
    public AchievementDefinitionResponse saveAchievement(Long id, AchievementDefinitionRequest request) {
        AchievementDefinition value = id == null ? new AchievementDefinition() : achievements.findById(id)
                .orElseThrow(() -> new ArenaProblem.NotFound("Conquista não encontrada."));
        String code = code(request.code());
        achievements.findByCode(code).filter(existing -> !Objects.equals(existing.getId(), id)).ifPresent(existing -> {
            throw new ArenaProblem.Conflict("Já existe uma conquista com este código.");
        });
        value.setCode(code); value.setName(request.name().trim()); value.setDescription(request.description().trim());
        value.setRarity(request.rarity().trim().toUpperCase(Locale.ROOT)); value.setRule(request.rule());
        value.setTarget(request.target()); value.setPointsReward(request.pointsReward());
        value.setActive(request.active() == null || request.active());
        return achievement(achievements.save(value));
    }

    @Transactional
    public void deactivateAchievement(Long id) {
        AchievementDefinition value = achievements.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Conquista não encontrada."));
        value.setActive(false);
    }

    @Transactional(readOnly = true)
    public List<ChallengeDefinitionResponse> challenges() {
        return challenges.findAll(Sort.by("expiresAt")).stream().map(this::challenge).toList();
    }

    @Transactional
    public ChallengeDefinitionResponse saveChallenge(Long id, ChallengeDefinitionRequest request) {
        if (!request.expiresAt().isAfter(request.startsAt()))
            throw new ArenaProblem.RuleViolation("O desafio deve terminar depois de começar.");
        ChallengeDefinition value = id == null ? new ChallengeDefinition() : challenges.findById(id)
                .orElseThrow(() -> new ArenaProblem.NotFound("Desafio não encontrado."));
        String code = code(request.code());
        challenges.findByCode(code).filter(existing -> !Objects.equals(existing.getId(), id)).ifPresent(existing -> {
            throw new ArenaProblem.Conflict("Já existe um desafio com este código.");
        });
        value.setCode(code); value.setName(request.name().trim()); value.setDescription(request.description().trim());
        value.setMetric(request.metric()); value.setTarget(request.target()); value.setRewardPoints(request.rewardPoints());
        value.setStartsAt(request.startsAt()); value.setExpiresAt(request.expiresAt());
        value.setActive(request.active() == null || request.active());
        return challenge(challenges.save(value));
    }

    @Transactional
    public void deactivateChallenge(Long id) {
        ChallengeDefinition value = challenges.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Desafio não encontrado."));
        value.setActive(false);
    }

    @Transactional(readOnly = true)
    public Page<AdminNotificationResponse> notifications(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt"));
        return notifications.findAll(pageable).map(this::notification);
    }

    @Transactional
    public NotificationDispatchResponse dispatch(AdminNotificationRequest request) {
        List<User> recipients = request.userId() == null ? users.findAll() : List.of(users.findById(request.userId())
                .orElseThrow(() -> new ArenaProblem.NotFound("Usuário não encontrado.")));
        recipients.forEach(user -> notificationService.create(user, request.type(), request.title().trim(),
                request.message().trim(), request.targetUrl()));
        return new NotificationDispatchResponse(recipients.size(), request.userId() == null ? "ALL_USERS" : "USER");
    }

    @Transactional
    public void deleteNotification(Long id) {
        if (!notifications.existsById(id)) throw new ArenaProblem.NotFound("Notificação não encontrada.");
        notifications.deleteById(id);
    }

    private AchievementDefinitionResponse achievement(AchievementDefinition value) {
        return new AchievementDefinitionResponse(value.getId(), value.getCode(), value.getName(), value.getDescription(),
                value.getRarity(), value.getRule(), value.getTarget(), value.getPointsReward(), value.isActive());
    }
    private ChallengeDefinitionResponse challenge(ChallengeDefinition value) {
        return new ChallengeDefinitionResponse(value.getId(), value.getCode(), value.getName(), value.getDescription(),
                value.getMetric(), value.getTarget(), value.getRewardPoints(), value.getStartsAt(), value.getExpiresAt(), value.isActive());
    }
    private AdminNotificationResponse notification(ArenaNotification value) {
        return new AdminNotificationResponse(value.getId(), value.getUser().getId(), value.getUser().getName(), value.getType(),
                value.getTitle(), value.getMessage(), value.getTargetUrl(), value.getCreatedAt(), value.getReadAt() != null);
    }
    private String code(String value) { return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_"); }
}
