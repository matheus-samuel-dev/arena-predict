package com.bolao.copa.arena;

import static com.bolao.copa.arena.api.ExperienceDtos.*;
import static com.bolao.copa.arena.api.ArenaDtos.PoolRequest;
import static org.assertj.core.api.Assertions.*;

import com.bolao.copa.arena.domain.ArenaEnums.ChallengeMetric;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.domain.ChallengeDefinition;
import com.bolao.copa.arena.api.ArenaDtos.RankingRow;
import com.bolao.copa.arena.config.ArenaExperienceDemoInitializer;
import com.bolao.copa.config.DemoParticipantCatalog;
import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class ArenaExperienceIntegrationTest {
    @Autowired UserRepository users;
    @Autowired PlayerProfileService profiles;
    @Autowired ProgressionService progression;
    @Autowired PointWalletService wallets;
    @Autowired CommunityService community;
    @Autowired ArenaPoolRankingService pools;
    @Autowired ArenaDashboardService dashboard;
    @Autowired ArenaEventRepository events;
    @Autowired PointLedgerRepository ledger;
    @Autowired ArenaNotificationRepository notifications;
    @Autowired UserAchievementRepository userAchievements;
    @Autowired UserChallengeRepository userChallenges;
    @Autowired ChallengeDefinitionRepository challengeDefinitions;
    @Autowired CommunityPostRepository communityPosts;
    @Autowired CommunityLikeRepository communityLikes;
    @Autowired PlayerProfileRepository profileRecords;
    @Autowired ArenaExperienceDemoInitializer demoProfiles;

    @Test
    @Transactional
    void demoCommunityStartsPopulatedAndEveryDemoIdentityHasAVisualAvatar() {
        for (var participant : DemoParticipantCatalog.participants()) {
            var user = users.findByEmail(participant.email()).orElseThrow();
            assertThat(profileRecords.findByUser(user).orElseThrow().getAvatarUrl())
                    .isEqualTo(participant.avatarUrl())
                    .isIn(DemoParticipantCatalog.arenaAvatars());
        }

        var current = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var feed = community.feed(0, 20, current);
        assertThat(feed.getTotalElements()).isGreaterThanOrEqualTo(5);
        assertThat(feed.getContent())
                .allMatch(post -> post.author() != null
                        && post.author().avatarUrl() != null
                        && post.author().avatarUrl().startsWith("/assets/avatars/"));
    }

    @Test
    @Transactional
    void bootstrapRepairsLegacyDemoAvatarWithoutOverwritingAnArenaChoice() {
        var sofia = users.findByEmail("sofia.martins@arenapredict.com").orElseThrow();
        var sofiaProfile = profileRecords.findByUser(sofia).orElseThrow();
        sofiaProfile.setAvatarUrl("/assets/avatars/sofia-martins.webp");

        var jogador = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var jogadorProfile = profileRecords.findByUser(jogador).orElseThrow();
        String chosenAvatar = "/assets/avatars/ana-ribeiro.webp";
        jogadorProfile.setAvatarUrl(chosenAvatar);
        profileRecords.saveAll(List.of(sofiaProfile, jogadorProfile));

        demoProfiles.seed();

        assertThat(profileRecords.findByUser(sofia).orElseThrow().getAvatarUrl())
                .isEqualTo(DemoParticipantCatalog.byEmail().get(sofia.getEmail()).avatarUrl());
        assertThat(profileRecords.findByUser(jogador).orElseThrow().getAvatarUrl())
                .isEqualTo(chosenAvatar);
    }

    @Test
    @Transactional
    void profileAndPreferencesArePersistedWithoutExposingPassword() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var updated = profiles.update(user, new ProfileUpdateRequest("Jogador Arena", user.getEmail(),
                "/assets/avatars/jogador-demo.webp", "Perfil público de demonstração", List.of("FOOTBALL", "CS2"), true));
        var preferences = profiles.preferences(user, new PreferenceUpdateRequest("light", "en-US", false, false));

        assertThat(updated.name()).isEqualTo("Jogador Arena");
        assertThat(updated.createdAt()).isEqualTo(user.getCreatedAt());
        assertThat(updated.favoriteSports()).containsExactly("FOOTBALL", "CS2");
        assertThat(preferences.theme()).isEqualTo("light");
        assertThat(preferences.notifications()).isFalse();
        assertThat(profiles.get(user).publicProfile()).isFalse();
    }

    @Test
    @Transactional
    void selectedAvatarPropagatesToGlobalAndPoolRankings() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        String selectedAvatar = "/assets/avatars/ana-ribeiro.webp";
        profiles.update(user, new ProfileUpdateRequest(user.getName(), user.getEmail(), selectedAvatar,
                "Perfil com avatar selecionado na galeria.", List.of("FOOTBALL", "CS2"), true));

        assertThat(pools.globalRanking(user))
                .filteredOn(row -> row.userId().equals(user.getId()))
                .extracting(RankingRow::avatarUrl)
                .containsExactly(selectedAvatar);

        var pool = pools.create(new PoolRequest("Liga de avatar", "Validação da identidade no ranking interno.",
                null, null, true, 20, 0, "Ranking por pontos virtuais.", null, null), user);
        assertThat(pools.poolRanking(pool.id(), user))
                .filteredOn(row -> row.userId().equals(user.getId()))
                .extracting(RankingRow::avatarUrl)
                .containsExactly(selectedAvatar);
    }

    @Test
    @Transactional
    void dashboardExposesCurrentAndBestStreakFromSettledPredictions() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();

        var playerDashboard = dashboard.dashboard(user);

        assertThat(playerDashboard.streak()).isZero();
        assertThat(playerDashboard.bestStreak()).isEqualTo(3);
        assertThat(playerDashboard.bestStreak()).isGreaterThanOrEqualTo(playerDashboard.streak());
    }

    @Test
    @Transactional
    void progressionQueriesAndDashboardNeverGrantRewardsOrCreateState() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        progression.refresh(user);
        long balanceBeforeQueries = wallets.wallet(user).balance();
        long ledgerBeforeQueries = ledger.count();
        long notificationsBeforeQueries = notifications.count();
        long achievementsBeforeQueries = userAchievements.count();
        long challengesBeforeQueries = userChallenges.count();

        var achievements = progression.achievements(user);
        var challenges = progression.challenges(user);
        dashboard.dashboard(user);

        assertThat(achievements).anyMatch(AchievementResponse::unlocked);
        assertThat(challenges).isNotEmpty();
        assertThat(wallets.wallet(user).balance()).isEqualTo(balanceBeforeQueries);
        assertThat(ledger.count()).isEqualTo(ledgerBeforeQueries);
        assertThat(notifications.count()).isEqualTo(notificationsBeforeQueries);
        assertThat(userAchievements.count()).isEqualTo(achievementsBeforeQueries);
        assertThat(userChallenges.count()).isEqualTo(challengesBeforeQueries);

        progression.refresh(user);
        assertThat(wallets.wallet(user).balance()).isEqualTo(balanceBeforeQueries);
        assertThat(ledger.count()).isEqualTo(ledgerBeforeQueries);
        assertThat(notifications.count()).isEqualTo(notificationsBeforeQueries);
    }

    @Test
    @Transactional
    void dashboardNeverFeaturesFinishedOrCancelledEvents() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var cancelled = events.findAll().stream()
                .filter(event -> event.getStatus() == EventStatus.CANCELLED)
                .findFirst()
                .orElseThrow();
        cancelled.setFeatured(true);
        events.saveAndFlush(cancelled);

        assertThat(dashboard.dashboard(user).featuredEvents())
                .extracting(event -> event.id())
                .doesNotContain(cancelled.getId());
    }

    @Test
    @Transactional
    void communityLikeAndReportAreIdempotentAndOwnershipIsEnforced() {
        var author = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var other = users.findByEmail("marina.costa@arenapredict.com").orElseThrow();
        var post = community.create(new PostRequest("Uma análise original para o próximo evento.", "Análise"), author);

        assertThat(community.like(post.id(), other)).satisfies(response -> {
            assertThat(response.likeCount()).isEqualTo(1);
            assertThat(response.likedByCurrentUser()).isTrue();
        });
        assertThat(community.like(post.id(), other).likeCount()).isEqualTo(1);
        assertThat(community.unlike(post.id(), other)).satisfies(response -> {
            assertThat(response.likeCount()).isZero();
            assertThat(response.likedByCurrentUser()).isFalse();
        });
        assertThat(community.unlike(post.id(), other).likeCount()).isZero();
        assertThat(community.like(post.id(), other).likeCount()).isEqualTo(1);
        assertThat(community.comment(post.id(), new CommentRequest("Boa leitura do confronto."), other).postId()).isEqualTo(post.id());
        community.report(post.id(), new ReportRequest("Revisão de moderação para teste"), other);
        community.report(post.id(), new ReportRequest("Motivo atualizado sem duplicar"), other);
        assertThat(community.reports(0, 20).getTotalElements()).isPositive();
        assertThatThrownBy(() -> community.removeOwnPost(post.id(), other)).isInstanceOf(AccessDeniedException.class);
        community.removeOwnPost(post.id(), author);
    }

    @Test
    @Transactional
    void demoPostReactionPersistsInFeedAndCanBeRestored() {
        var current = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var post = communityPosts.findBySourceKey("demo-community-analysis").orElseThrow();
        long initialCount = communityLikes.countByPost(post);

        assertThat(community.like(post.getId(), current).likedByCurrentUser()).isTrue();
        assertThat(community.unlike(post.getId(), current)).satisfies(response -> {
            assertThat(response.likeCount()).isEqualTo(initialCount - 1);
            assertThat(response.likedByCurrentUser()).isFalse();
        });
        assertThat(community.feed(0, 20, current).getContent())
                .filteredOn(response -> response.id().equals(post.getId()))
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.likeCount()).isEqualTo(initialCount - 1);
                    assertThat(response.likedByCurrentUser()).isFalse();
                });

        assertThat(community.like(post.getId(), current)).satisfies(response -> {
            assertThat(response.likeCount()).isEqualTo(initialCount);
            assertThat(response.likedByCurrentUser()).isTrue();
        });
    }

    @Test
    void concurrentLikesCreateOneReaction() throws Exception {
        var author = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var liker = users.findByEmail("marina.costa@arenapredict.com").orElseThrow();
        var post = community.create(new PostRequest(
                "Publicação criada para validar reações concorrentes.", "Concorrência"), author);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return community.like(post.id(), users.findById(liker.getId()).orElseThrow());
            });
            var second = executor.submit(() -> {
                start.await();
                return community.like(post.id(), users.findById(liker.getId()).orElseThrow());
            });
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS).likeCount()).isEqualTo(1);
            assertThat(second.get(10, TimeUnit.SECONDS).likeCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(communityLikes.countByPost(communityPosts.findById(post.id()).orElseThrow())).isEqualTo(1);
    }

    @Test
    @Transactional
    void privatePoolsAreVisibleOnlyToMembersAndInviteCodeIsNeverPublic() {
        var owner = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var outsider = users.findByEmail("marina.costa@arenapredict.com").orElseThrow();
        var privatePool = pools.create(new PoolRequest("Liga privada", "Somente convidados", null, null,
                false, 20, 300, "Ranking por pontos virtuais.", null, null), owner);
        var publicPool = pools.create(new PoolRequest("Liga pública", "Visível na arena", null, null,
                true, 20, 300, "Ranking por pontos virtuais.", null, null), owner);

        assertThat(privatePool.inviteCode()).isNotBlank();
        assertThat(pools.list(outsider)).extracting(value -> value.id()).doesNotContain(privatePool.id());
        assertThatThrownBy(() -> pools.get(privatePool.id(), outsider)).isInstanceOf(ArenaProblem.NotFound.class);
        assertThat(pools.get(publicPool.id(), outsider).inviteCode()).isNull();
        assertThat(pools.get(publicPool.id(), outsider).joined()).isFalse();
    }

    @Test
    void concurrentPoolJoinsCannotExceedCapacity() throws Exception {
        var owner = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var firstCandidate = users.findByEmail("marina.costa@arenapredict.com").orElseThrow();
        var secondCandidate = users.findByEmail("rafael.lima@arenapredict.com").orElseThrow();
        var pool = pools.create(new PoolRequest("Bolão limitado " + UUID.randomUUID(),
                "Validação de capacidade concorrente", null, null, true, 2, 0,
                "Até duas pessoas, incluindo a pessoa criadora.", null, null), owner);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> attemptPoolJoin(pool.id(), firstCandidate.getId(), start));
            var second = executor.submit(() -> attemptPoolJoin(pool.id(), secondCandidate.getId(), start));
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        assertThat(pools.get(pool.id(), owner).participantCount()).isEqualTo(2);
    }

    @Test
    @Transactional
    void welcomeBalanceDoesNotCountAsActivityXp() {
        User newcomer = new User();
        newcomer.setName("Participante sem atividade");
        newcomer.setEmail("xp-zero-" + UUID.randomUUID() + "@arenapredict.test");
        newcomer.setPasswordHash("não-utilizada-neste-teste");
        newcomer.setRole(UserRole.PARTICIPANTE);
        newcomer = users.saveAndFlush(newcomer);

        var wallet = wallets.wallet(newcomer);
        var playerDashboard = dashboard.dashboard(newcomer);

        assertThat(wallet.balance()).isEqualTo(PointWalletService.INITIAL_DEMO_POINTS);
        assertThat(wallet.lifetimeEarned()).isZero();
        assertThat(playerDashboard.xp()).isZero();
        assertThat(playerDashboard.level()).isEqualTo(1);
        assertThat(playerDashboard.nextLevelXp()).isEqualTo(5_000);
    }

    @Test
    @Transactional
    void challengeCanRewardTheSameDefinitionInANewWindowOnlyOnce() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        Instant now = Instant.now();
        String code = "RECURRING_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ChallengeDefinition definition = new ChallengeDefinition();
        definition.setCode(code);
        definition.setName("Desafio recorrente de teste");
        definition.setDescription("Valida recompensas independentes por janela.");
        definition.setMetric(ChallengeMetric.POOL_MEMBER_COUNT);
        definition.setTarget(1);
        definition.setRewardPoints(37);
        definition.setStartsAt(now.minusSeconds(7_200));
        definition.setExpiresAt(now.plusSeconds(7_200));
        definition = challengeDefinitions.saveAndFlush(definition);

        long before = wallets.wallet(user).balance();
        progression.refresh(user);
        progression.refresh(user);
        assertThat(wallets.wallet(user).balance()).isEqualTo(before + 37);

        definition.setStartsAt(now.minusSeconds(3_600));
        definition.setExpiresAt(now.plusSeconds(10_800));
        challengeDefinitions.saveAndFlush(definition);
        progression.refresh(user);
        progression.refresh(user);

        assertThat(wallets.wallet(user).balance()).isEqualTo(before + 74);
        var state = userChallenges.findByUserAndChallenge(user, definition).orElseThrow();
        assertThat(state.getWindowStart()).isEqualTo(definition.getStartsAt());
        assertThat(state.getCompletedAt()).isNotNull();
    }

    @Test
    void concurrentProgressionRefreshGrantsOneReward() throws Exception {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        progression.refresh(user);
        Instant now = Instant.now();
        String code = "CONCURRENT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ChallengeDefinition definition = new ChallengeDefinition();
        definition.setCode(code);
        definition.setName("Desafio concorrente de teste");
        definition.setDescription("Concede uma única recompensa sob chamadas paralelas.");
        definition.setMetric(ChallengeMetric.POOL_MEMBER_COUNT);
        definition.setTarget(1);
        definition.setRewardPoints(41);
        definition.setStartsAt(now.minusSeconds(60));
        definition.setExpiresAt(now.plusSeconds(3_600));
        definition = challengeDefinitions.saveAndFlush(definition);
        long before = wallets.wallet(user).balance();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                progression.refresh(users.findById(user.getId()).orElseThrow());
                return true;
            });
            var second = executor.submit(() -> {
                start.await();
                progression.refresh(users.findById(user.getId()).orElseThrow());
                return true;
            });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        var reloaded = users.findById(user.getId()).orElseThrow();
        assertThat(wallets.wallet(reloaded).balance()).isEqualTo(before + 41);
        assertThat(ledger.findByIdempotencyKey("challenge:user:" + user.getId() + ":" + code + ":"
                + definition.getStartsAt().toEpochMilli())).isPresent();
        assertThat(userChallenges.findByUserAndChallenge(reloaded, definition).orElseThrow().isRewardGranted()).isTrue();
    }

    private boolean attemptPoolJoin(Long poolId, Long userId, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            pools.joinPublic(poolId, users.findById(userId).orElseThrow());
            return true;
        } catch (ArenaProblem.Conflict full) {
            return false;
        }
    }
}
