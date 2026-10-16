package com.bolao.copa.arena;

import static com.bolao.copa.arena.api.ArenaDtos.*;
import static org.assertj.core.api.Assertions.*;

import com.bolao.copa.arena.config.DemoPortfolioSeedLifecycle;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.demo.*;
import com.bolao.copa.config.DemoParticipantCatalog;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.support.RegularTestUsers;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties={"app.demo.controlled-enabled=true", "app.demo.portfolio-seed-enabled=true", "app.demo.maintenance-ms=86400000"})
@ActiveProfiles("test") @Transactional
class DemoPortfolioSeedIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> Optional.ofNullable(System.getenv("SPRING_DATASOURCE_URL"))
                .orElse("jdbc:h2:mem:demo_portfolio;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"));
    }
    // Seed commands run inside each rollback transaction, never at test-context startup.
    @MockBean DemoPortfolioSeedLifecycle lifecycle;
    @Autowired DemoPortfolioSeedService seed;
    @Autowired DemoScenarioService scenario;
    @Autowired UserRepository users;
    @Autowired ArenaPredictionRepository predictions;
    @Autowired ArenaEventRepository events;
    @Autowired ChampionshipRepository championships;
    @Autowired PointWalletService wallets;
    @Autowired PointLedgerRepository ledger;
    @Autowired ArenaPoolRepository pools;
    @Autowired ArenaPoolRankingService rankings;
    @Autowired ArenaDashboardService dashboards;
    @Autowired ArenaPredictionService commands;
    @Autowired MarketTemplateService templates;
    @Autowired MarketOptionRepository options;
    @Autowired ProgressionService progression;
    @Autowired JdbcTemplate db;
    @Autowired EntityManager em;
    @Autowired AchievementDefinitionRepository achievementDefinitions;

    @Test
    void futureDemoGoalsUseExistingProgressionAndNeverGrantOrExposeGoalsToRegularAccounts() {
        seed.seed(); em.flush();
        User player = player();
        var goals = progression.achievements(player).stream().filter(a -> a.code().startsWith("DEMO_")).toList();
        assertThat(goals).hasSize(3).allSatisfy(goal -> {
            assertThat(goal.unlocked()).isFalse();
            assertThat(goal.unlockedAt()).isNull();
            assertThat(goal.progress()).isPositive().isLessThan(goal.target());
        });
        long balance = wallets.wallet(player).balance();
        long definitions = achievementDefinitions.count();
        seed.seed(); progression.refresh(player); em.flush();
        assertThat(achievementDefinitions.count()).isEqualTo(definitions);
        assertThat(wallets.wallet(player).balance()).isEqualTo(balance);

        // A later genuine threshold crossing uses the same reward command, exactly once.
        var goal = achievementDefinitions.findByCode("DEMO_FIFTY_PREDICTIONS").orElseThrow();
        goal.setTarget(goals.stream().filter(a -> a.code().equals(goal.getCode())).findFirst().orElseThrow().progress());
        progression.refresh(player); progression.refresh(player); em.flush();
        assertThat(wallets.wallet(player).balance()).isEqualTo(balance + goal.getPointsReward());
        assertThat(progression.achievements(player).stream().filter(a -> a.code().equals(goal.getCode())).findFirst().orElseThrow().unlocked()).isTrue();

        User regular = RegularTestUsers.freshParticipant(users);
        long regularBalance = wallets.wallet(regular).balance();
        goal.setTarget(1); // Demo-only definitions must not even create progress state for normal accounts.
        progression.refresh(regular); em.flush();
        assertThat(progression.achievements(regular)).noneMatch(a -> a.code().startsWith("DEMO_"));
        assertThat(wallets.wallet(regular).balance()).isEqualTo(regularBalance);
        assertThat(db.queryForObject("select count(*) from arena_user_achievements ua join arena_achievement_definitions a on a.id=ua.achievement_id where ua.user_id=? and a.demo_only=true", Long.class, regular.getId())).isZero();
    }

    @Test
    void persistsVariedHistoryThroughSettlementAndReconcilesEveryWalletWithoutDuplicateReplay() {
        long beforePredictions = predictions.count(), beforeLedger = ledger.count();
        var legacyPool = pools.findByInviteCodeIgnoreCase("AMIGOS26").orElseThrow();
        var legacyOwner = users.findByEmailIgnoreCase("marina.costa@arenapredict.com").orElseThrow();
        legacyPool.setOwner(legacyOwner); pools.saveAndFlush(legacyPool);
        seed.seed(); em.flush();
        var created = portfolioPredictions();
        assertThat(created).hasSize(456);
        assertThat(created.stream().filter(p -> p.getStatus() == PredictionStatus.ACTIVE)).hasSize(48);
        assertThat(created.stream().filter(p -> p.getStatus() == PredictionStatus.WON)).isNotEmpty();
        assertThat(created.stream().filter(p -> p.getStatus() == PredictionStatus.LOST)).isNotEmpty();
        assertThat(created.stream().map(p -> p.getEvent().getChampionship().getSport().getCode()).distinct()).hasSize(6);
        assertThat(created.stream().map(p -> p.getMarket().getTemplateCode()).distinct()).hasSize(10);
        for (var prediction : created) {
            assertThat(prediction.getEvent().isDemo()).isTrue();
            assertThat(prediction.getEvent().getExternalProvider()).isNull();
            if (prediction.getStatus() == PredictionStatus.WON)
                assertThat(prediction.getRewardedPoints()).isEqualTo(prediction.getPotentialPoints());
            else assertThat(prediction.getRewardedPoints()).isZero();
        }
        for (var person : DemoParticipantCatalog.participants()) {
            User user = users.findByEmailIgnoreCase(person.email()).orElseThrow();
            var wallet = wallets.wallet(user);
            Long sum = db.queryForObject("select sum(t.amount) from arena_point_transactions t join arena_point_wallets w on w.id=t.wallet_id where w.user_id=?", Long.class, user.getId());
            assertThat(wallet.balance()).isEqualTo(sum);
            assertThat(wallet.balance()).isNotNegative();
            long wins = predictions.findByUserOrderByPlacedAtDesc(user).stream().filter(p -> p.getStatus() == PredictionStatus.WON).count();
            var row = rankings.ranking(user, RankingPeriod.ALL, RankingScope.GLOBAL, null, "DEMO").stream().filter(RankingRow::currentUser).findFirst().orElseThrow();
            assertThat(row.correctPredictions()).isEqualTo(wins);
            assertThat(row.accuracy()).isEqualTo(Math.round(wins * 10000.0 / row.totalPredictions()) / 100.0);
        }
        User player = player();
        assertThat(dashboards.dashboard(player).performance()).hasSize(12);
        assertThat(progression.achievements(player).stream().filter(a -> a.unlocked())).isNotEmpty();
        for (var challenge : progression.challenges(player)) {
            assertThat(challenge.expiresAt()).isAfter(Instant.now());
            if (challenge.completed()) assertThat(challenge.progress()).isGreaterThanOrEqualTo(challenge.target());
        }
        var social = pools.findAll().stream().filter(p -> "Bolão de Demonstração".equals(p.getName())).findFirst().orElseThrow();
        assertThat(rankings.poolRanking(social.getId(), player).stream().filter(r -> r.points() > 0)).isNotEmpty();
        assertThat(pools.findById(legacyPool.getId()).orElseThrow().getOwner().getId()).isEqualTo(legacyOwner.getId());
        long afterLedger = ledger.count();
        long afterPools = pools.count();
        assertThat(afterLedger).isGreaterThan(beforeLedger);
        seed.seed(); em.flush();
        assertThat(predictions.count()).isEqualTo(beforePredictions + 456);
        assertThat(ledger.count()).isEqualTo(afterLedger);
        assertThat(pools.count()).isEqualTo(afterPools);
    }

    @Test
    void historicalActivityDoesNotCountAsTodayAndCannotTargetAnOfficialEvent() {
        User player = player();
        int dailyBefore = progression.challenges(player).stream().filter(c -> c.code().equals("DAILY_THREE")).findFirst().orElseThrow().progress();
        var event = events.findByExternalKey("demo-cs2-open").orElseThrow();
        var market = templates.generateDemoSeed(event.getId(), Set.of("SERIES_WINNER")).getFirst();
        var option = options.findByMarketOrderByIdAsc(market).getFirst();
        var historical = Instant.now().minus(Duration.ofDays(20));
        var response = commands.placeDemoSeed(new PlacePredictionRequest(event.getId(), market.getId(), option.getId(), 30, null, "historical-date-contract"), null, player, historical);
        assertThat(response.placedAt()).isEqualTo(historical);
        assertThat(progression.challenges(player).stream().filter(c -> c.code().equals("DAILY_THREE")).findFirst().orElseThrow().progress()).isEqualTo(dailyBefore);

        var championship = new Championship(); championship.setSport(event.getChampionship().getSport());
        championship.setName("Official fixture"); championship.setSlug("official-portfolio-contract");
        championship.setExternalProvider("PANDASCORE"); championship.setExternalId("official-portfolio-contract"); championships.saveAndFlush(championship);
        var official = new ArenaEvent(); official.setChampionship(championship); official.setExternalProvider("PANDASCORE");
        official.setExternalId("official-portfolio-contract"); official.setExternalKey("official-portfolio-contract");
        official.setTitle("Official fixture"); official.setStartsAt(event.getStartsAt()); official.setPredictionClosesAt(event.getStartsAt()); events.saveAndFlush(official);
        assertThatThrownBy(() -> commands.placeDemoSeed(new PlacePredictionRequest(official.getId(), market.getId(), option.getId(), 30, null, "forbidden-seed"), null, player, historical))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void resetPreservesPortfolioHistoryAndNormalAccountsWhileAdminStaysAnOperator() {
        User regular = RegularTestUsers.freshParticipant(users);
        long regularBalance = wallets.wallet(regular).balance();
        User admin = users.findByEmailIgnoreCase("admin@arenapredict.com").orElseThrow();
        long adminPredictions = predictions.findByUserOrderByPlacedAtDesc(admin).size();
        seed.seed(); em.flush();
        var ids = portfolioPredictions().stream().map(ArenaPrediction::getId).toList();
        long generation = scenario.scenario(player()).generation();
        var reset = scenario.reset(generation, admin);
        scenario.reset(generation, admin);
        assertThat(reset.generation()).isEqualTo(generation + 1);
        assertThat(portfolioPredictions().stream().map(ArenaPrediction::getId)).containsExactlyElementsOf(ids);
        assertThat(wallets.wallet(regular).balance()).isEqualTo(regularBalance);
        assertThat(predictions.findByUserOrderByPlacedAtDesc(regular)).isEmpty();
        assertThat(predictions.findByUserOrderByPlacedAtDesc(admin)).hasSize((int) adminPredictions);
        assertThat(events.findByExternalKey(DemoPortfolioSeedService.VERSION + ":history:demo-cs2-open").orElseThrow().getStatus()).isEqualTo(EventStatus.FINISHED);
    }

    private User player() { return users.findByEmailIgnoreCase("jogador@arenapredict.com").orElseThrow(); }
    private List<ArenaPrediction> portfolioPredictions() {
        return DemoParticipantCatalog.participants().stream().flatMap(person -> predictions.findByUserOrderByPlacedAtDesc(users.findByEmailIgnoreCase(person.email()).orElseThrow()).stream())
                .filter(p -> p.getEvent().getExternalKey().startsWith(DemoPortfolioSeedService.VERSION + ":")).toList();
    }
}
