package com.bolao.copa.arena.config;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.entity.User;
import com.bolao.copa.config.DemoParticipantCatalog;
import com.bolao.copa.repository.UserRepository;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class ArenaExperienceDemoInitializer {
    private static final Map<String, String> DEMO_AVATARS = Map.of(
            "admin@arenapredict.com", "/assets/avatars/admin-demo.webp"
    );
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
    @Order(0)
    @Transactional
    public void seed() {
        List<User> identityUsers = users.findAll().stream()
                .filter(user -> user.getEmail().toLowerCase(Locale.ROOT).endsWith("@arenapredict.com"))
                .toList();
        identityUsers.forEach(this::ensureDemoProfile);
        List<User> demoUsers = identityUsers.stream()
                .filter(user -> user.getRole().canonical() == com.bolao.copa.entity.UserRole.PARTICIPANTE)
                .toList();

        achievement("FIRST_PREDICTION", "Primeiro palpite", "Registre seu primeiro palpite válido.", "COMMON", AchievementRule.FIRST_PREDICTION, 1, 100);
        achievement("FIRST_WIN", "Leitura certeira", "Acerte seu primeiro mercado de previsão.", "COMMON", AchievementRule.FIRST_WIN, 1, 200);
        achievement("TEN_PREDICTIONS", "Analista dedicado", "Registre dez palpites válidos.", "RARE", AchievementRule.PREDICTION_COUNT, 10, 350);
        achievement("FIVE_WINS", "Sequência de precisão", "Conquiste cinco palpites vencedores.", "EPIC", AchievementRule.WON_COUNT, 5, 500);
        achievement("POOL_MEMBER", "Em boa companhia", "Participe de um bolão ou liga.", "COMMON", AchievementRule.POOL_MEMBER, 1, 150);

        seedChallengeWindows();

        if (demoUsers.isEmpty()) return;
        User jogador = user("jogador@arenapredict.com", demoUsers);
        User beatriz = user("beatriz.nunes@arenapredict.com", demoUsers);
        User marina = user("marina.costa@arenapredict.com", demoUsers);
        User rafael = user("rafael.lima@arenapredict.com", demoUsers);
        User camila = user("camila.rocha@arenapredict.com", demoUsers);
        User lucas = user("lucas.almeida@arenapredict.com", demoUsers);
        User ana = user("ana.ribeiro@arenapredict.com", demoUsers);
        User diego = user("diego.ferreira@arenapredict.com", demoUsers);

        CommunityPost football = post("demo-community-analysis", beatriz,
                "Palmeiras x Flamengo está ao vivo no ambiente demo. Estou acompanhando o ritmo do segundo tempo e prefiro leituras consistentes a multiplicadores altos. O que vocês observaram?",
                "Brasileirão", Duration.ofMinutes(24));
        CommunityPost esports = post("demo-community-esports", rafael,
                "FURIA x NAVI: o controle econômico está mudando o segundo mapa. Como os dados ao vivo são simulados, estou usando o evento para comparar cenários sem confundir com uma transmissão oficial.",
                "Counter-Strike 2", Duration.ofMinutes(52));
        CommunityPost pools = post("demo-community-pools", marina,
                "A Liga Arena 2026 ficou mais interessante com a nova rodada. Minha estratégia é diversificar modalidades e acompanhar a regularidade do ranking, sempre usando apenas pontos virtuais.",
                "Bolões e ligas", Duration.ofHours(2));
        CommunityPost valorant = post("demo-community-valorant", camila,
                "Leviatán x LOUD promete uma série equilibrada. Separei os mapas recentes e o prazo de fechamento antes de registrar meu palpite demo. Qual fator pesa mais para vocês?",
                "Valorant", Duration.ofHours(4));
        CommunityPost progression = post("demo-community-progression", lucas,
                "Fechei a semana com uma sequência melhor depois de reduzir palpites impulsivos. O painel de precisão e os desafios ajudam bastante a revisar a própria evolução.",
                "Desempenho", Duration.ofHours(7));

        commentIfEmpty(football, diego, "Também estou olhando a pressão alta e o volume de finalizações, sem concentrar todos os pontos em um único mercado.");
        commentIfEmpty(esports, ana, "Boa leitura. Em séries melhor de três, a adaptação entre mapas costuma ser tão importante quanto a abertura.");
        commentIfEmpty(pools, jogador, "Diversificar funcionou para mim também. O resumo do bolão ajuda a enxergar onde a pontuação foi construída.");
        commentIfEmpty(valorant, beatriz, "Para mim, composição e consistência defensiva pesam mais do que uma rodada isolada.");
        commentIfEmpty(progression, marina, "A sequência fica mais sustentável quando a decisão parte dos dados e não do impulso.");

        like(football, jogador); like(football, marina); like(football, camila);
        like(esports, jogador); like(esports, diego); like(esports, lucas);
        like(pools, beatriz); like(pools, ana);
        like(valorant, rafael); like(valorant, marina);
        like(progression, jogador); like(progression, camila); like(progression, diego);
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
    @Transactional
    public void refreshChallengeWindows() {
        seedChallengeWindows();
    }

    private void seedChallengeWindows() {
        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        challenge("DAILY_THREE", "Trinca do dia", "Registre três palpites válidos hoje.", ChallengeMetric.PREDICTION_COUNT, 3, 180, dayStart, dayStart.plus(Duration.ofDays(1)));
        challenge("WEEKLY_EXPLORER", "Explorador da arena", "Participe de previsões em duas modalidades nesta semana.", ChallengeMetric.SPORT_VARIETY, 2, 250, dayStart.minus(Duration.ofDays(6)), dayStart.plus(Duration.ofDays(1)));
        challenge("WEEKLY_WINNER", "Semana certeira", "Acerte dois palpites durante o desafio.", ChallengeMetric.WON_COUNT, 2, 300, dayStart.minus(Duration.ofDays(6)), dayStart.plus(Duration.ofDays(1)));
    }

    private void ensureDemoProfile(User user) {
        PlayerProfile profile = profiles.findByUser(user).orElseGet(() -> {
            PlayerProfile created = new PlayerProfile();
            created.setUser(user);
            return created;
        });
        boolean changed = profile.getId() == null;
        String email = user.getEmail().toLowerCase(Locale.ROOT);
        var participant = DemoParticipantCatalog.byEmail().get(email);
        String expectedAvatar = participant != null ? participant.avatarUrl() : DEMO_AVATARS.get(email);
        if ((profile.getAvatarUrl() == null || profile.getAvatarUrl().isBlank()) && expectedAvatar != null) {
            profile.setAvatarUrl(expectedAvatar);
            changed = true;
        }
        if (profile.getBio() == null || profile.getBio().isBlank()) {
            profile.setBio(demoBio(email));
            changed = true;
        }
        if (profile.getFavoriteSports() == null || profile.getFavoriteSports().isBlank()) {
            profile.setFavoriteSports(demoSports(email));
            changed = true;
        }
        if (changed) {
            profile.touch();
            profiles.save(profile);
        }
    }

    private String demoBio(String email) {
        return switch (email) {
            case "beatriz.nunes@arenapredict.com" -> "Apaixonada por futebol, dados e debates esportivos respeitosos.";
            case "marina.costa@arenapredict.com" -> "Analista multiesportiva e organizadora da Liga Arena 2026.";
            case "rafael.lima@arenapredict.com" -> "Acompanha Counter-Strike 2 e transforma mapas em leituras objetivas.";
            case "camila.rocha@arenapredict.com" -> "Fã de Valorant, estatísticas e estratégias de longo prazo.";
            case "lucas.almeida@arenapredict.com" -> "Competidor consistente, focado em evolução e precisão.";
            case "ana.ribeiro@arenapredict.com" -> "Torcedora experiente e presença ativa nas discussões da comunidade.";
            case "diego.ferreira@arenapredict.com" -> "Explora futebol, basquete e desafios semanais na ArenaPredict.";
            case "admin@arenapredict.com" -> "Responsável pela operação do ambiente demonstrativo ArenaPredict.";
            default -> "Analista multiesportivo na ArenaPredict.";
        };
    }

    private String demoSports(String email) {
        return switch (email) {
            case "rafael.lima@arenapredict.com" -> "CS2,FOOTBALL";
            case "camila.rocha@arenapredict.com" -> "VALORANT,BASKETBALL";
            case "lucas.almeida@arenapredict.com" -> "FOOTBALL,TENNIS,MOTORSPORT";
            case "ana.ribeiro@arenapredict.com" -> "FOOTBALL,VOLLEYBALL";
            case "diego.ferreira@arenapredict.com" -> "BASKETBALL,FOOTBALL";
            default -> "FOOTBALL,CS2,BASKETBALL";
        };
    }

    private void achievement(String code, String name, String description, String rarity, AchievementRule rule, int target, int reward) {
        achievements.findByCode(code).orElseGet(() -> { AchievementDefinition value = new AchievementDefinition(); value.setCode(code); value.setName(name); value.setDescription(description); value.setRarity(rarity); value.setRule(rule); value.setTarget(target); value.setPointsReward(reward); return achievements.save(value); });
    }
    private void challenge(String code, String name, String description, ChallengeMetric metric, int target, int reward, Instant starts, Instant expires) {
        ChallengeDefinition value = challenges.findByCode(code).orElse(null);
        if (value == null) {
            value = new ChallengeDefinition();
            value.setCode(code);
            value.setName(name);
            value.setDescription(description);
            value.setMetric(metric);
            value.setTarget(target);
            value.setRewardPoints(reward);
        }
        value.setStartsAt(starts);
        value.setExpiresAt(expires);
        challenges.save(value);
    }
    private User user(String email, List<User> fallback) {
        return users.findByEmailIgnoreCase(email).orElse(fallback.getFirst());
    }
    private CommunityPost post(String sourceKey, User author, String content, String topic, Duration age) {
        CommunityPost value = posts.findBySourceKey(sourceKey).orElse(null);
        if (value == null) {
            value = new CommunityPost();
            value.setSourceKey(sourceKey);
            value.setCreatedAt(Instant.now().minus(age));
        }
        value.setAuthor(author);
        value.setContent(content);
        value.setTopic(topic);
        return posts.save(value);
    }
    private void commentIfEmpty(CommunityPost post, User author, String content) {
        if (comments.countByPostAndStatus(post, ContentStatus.PUBLISHED) != 0) return;
        CommunityComment comment = new CommunityComment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent(content);
        comments.save(comment);
    }
    private void like(CommunityPost post, User user) {
        if (likes.findByPostAndUser(post, user).isPresent()) return;
        CommunityLike like = new CommunityLike();
        like.setPost(post);
        like.setUser(user);
        likes.save(like);
    }
}
