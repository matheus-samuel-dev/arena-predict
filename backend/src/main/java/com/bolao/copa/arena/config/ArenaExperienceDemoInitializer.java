package com.bolao.copa.arena.config;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import java.time.*;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true", matchIfMissing = true)
public class ArenaExperienceDemoInitializer {
    private final UserRepository users;
    private final PlayerProfileRepository profiles;
    private final AchievementDefinitionRepository achievements;
    private final ChallengeDefinitionRepository challenges;
    private final CommunityPostRepository posts;
    private final CommunityCommentRepository comments;
    private final CommunityLikeRepository likes;

    public ArenaExperienceDemoInitializer(UserRepository users, PlayerProfileRepository profiles,
                                          AchievementDefinitionRepository achievements,
                                          ChallengeDefinitionRepository challenges,
                                          CommunityPostRepository posts, CommunityCommentRepository comments,
                                          CommunityLikeRepository likes) {
        this.users = users; this.profiles = profiles; this.achievements = achievements; this.challenges = challenges;
        this.posts = posts; this.comments = comments; this.likes = likes;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        List<User> demoUsers = users.findAll().stream().filter(user -> user.getEmail().endsWith("@arenapredict.com") || user.getEmail().endsWith("@bolao.com")).toList();
        demoUsers.forEach(user -> profiles.findByUser(user).orElseGet(() -> { PlayerProfile profile = new PlayerProfile(); profile.setUser(user); profile.setBio("Analista multiesportivo na ArenaPredict."); profile.setFavoriteSports("FOOTBALL,CS2,BASKETBALL"); return profiles.save(profile); }));

        achievement("FIRST_PREDICTION", "Primeiro palpite", "Registre seu primeiro palpite válido.", "COMMON", AchievementRule.FIRST_PREDICTION, 1, 100);
        achievement("FIRST_WIN", "Leitura certeira", "Acerte seu primeiro mercado de previsão.", "COMMON", AchievementRule.FIRST_WIN, 1, 200);
        achievement("TEN_PREDICTIONS", "Analista dedicado", "Registre dez palpites válidos.", "RARE", AchievementRule.PREDICTION_COUNT, 10, 350);
        achievement("FIVE_WINS", "Sequência de precisão", "Conquiste cinco palpites vencedores.", "EPIC", AchievementRule.WON_COUNT, 5, 500);
        achievement("POOL_MEMBER", "Em boa companhia", "Participe de um bolão ou liga.", "COMMON", AchievementRule.POOL_MEMBER, 1, 150);

        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        challenge("DAILY_THREE", "Trinca do dia", "Registre três palpites válidos hoje.", ChallengeMetric.PREDICTION_COUNT, 3, 180, dayStart, dayStart.plus(Duration.ofDays(1)));
        challenge("WEEKLY_EXPLORER", "Explorador da arena", "Participe de previsões em duas modalidades nesta semana.", ChallengeMetric.SPORT_VARIETY, 2, 250, dayStart.minus(Duration.ofDays(6)), dayStart.plus(Duration.ofDays(1)));
        challenge("WEEKLY_WINNER", "Semana certeira", "Acerte dois palpites durante o desafio.", ChallengeMetric.WON_COUNT, 2, 300, dayStart.minus(Duration.ofDays(6)), dayStart.plus(Duration.ofDays(1)));

        if (demoUsers.isEmpty()) return;
        User first = demoUsers.stream().filter(user -> !user.getRole().name().equals("ADMIN")).findFirst().orElse(demoUsers.getFirst());
        User second = demoUsers.stream().filter(user -> !user.getId().equals(first.getId())).findFirst().orElse(first);
        CommunityPost analysis = post("demo-community-analysis", first, "Minha leitura para a rodada: consistência vale mais que multiplicadores altos. Quais mercados vocês priorizam?", "Análises multiesportivas");
        CommunityPost esports = post("demo-community-esports", second, "No CS2 demo, o controle econômico está fazendo diferença no segundo mapa. Dados ao vivo são identificados como simulação.", "eSports");
        if (comments.countByPostAndStatus(analysis, ContentStatus.PUBLISHED) == 0 && !first.getId().equals(second.getId())) {
            CommunityComment comment = new CommunityComment(); comment.setPost(analysis); comment.setAuthor(second); comment.setContent("Também considero o prazo de fechamento e evito concentrar todos os pontos em um único evento."); comments.save(comment);
        }
        if (likes.findByPostAndUser(esports, first).isEmpty()) { CommunityLike like = new CommunityLike(); like.setPost(esports); like.setUser(first); likes.save(like); }
    }

    private void achievement(String code, String name, String description, String rarity, AchievementRule rule, int target, int reward) {
        achievements.findByCode(code).orElseGet(() -> { AchievementDefinition value = new AchievementDefinition(); value.setCode(code); value.setName(name); value.setDescription(description); value.setRarity(rarity); value.setRule(rule); value.setTarget(target); value.setPointsReward(reward); return achievements.save(value); });
    }
    private void challenge(String code, String name, String description, ChallengeMetric metric, int target, int reward, Instant starts, Instant expires) {
        ChallengeDefinition value = challenges.findByCode(code).orElseGet(ChallengeDefinition::new); value.setCode(code); value.setName(name); value.setDescription(description); value.setMetric(metric); value.setTarget(target); value.setRewardPoints(reward); value.setStartsAt(starts); value.setExpiresAt(expires); challenges.save(value);
    }
    private CommunityPost post(String sourceKey, User author, String content, String topic) {
        return posts.findBySourceKey(sourceKey).orElseGet(() -> { CommunityPost value = new CommunityPost(); value.setSourceKey(sourceKey); value.setAuthor(author); value.setContent(content); value.setTopic(topic); return posts.save(value); });
    }
}
