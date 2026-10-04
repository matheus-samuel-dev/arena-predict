package com.bolao.copa.arena;

import static com.bolao.copa.arena.api.ArenaDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bolao.copa.arena.api.DemoScenarioDtos;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.demo.*;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.security.JwtService;
import com.bolao.copa.support.RegularTestUsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.*;

@SpringBootTest(properties={"app.demo.controlled-enabled=true", "app.demo.maintenance-ms=86400000"})
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional @DirtiesContext
class DemoScenarioIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> Optional.ofNullable(System.getenv("SPRING_DATASOURCE_URL"))
                .orElse("jdbc:h2:mem:controlled_demo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"));
    }
    @Autowired DemoScenarioService demo;
    @Autowired ArenaPredictionService commands;
    @Autowired ArenaEventRepository events;
    @Autowired ArenaPredictionRepository predictions;
    @Autowired PredictionMarketRepository markets;
    @Autowired PointLedgerRepository ledger;
    @Autowired ChampionshipRepository championships;
    @Autowired CompetitorRepository teams;
    @Autowired UserRepository users;
    @Autowired PointWalletService wallets;
    @Autowired EntityManager em;
    @Autowired MockMvc http;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;

    @Test void genericAdministrationKeepsControlledDemoReadOnlyIncludingItsCatalogAndMarkets() throws Exception {
        var scenario = demo.scenario(player());
        var event = scenario.event();
        var market = event.markets().getFirst();
        String authorization = "Bearer " + jwt.generate(RegularTestUsers.admin(users));
        long eventCount = events.count(), marketCount = markets.count(), entries = ledger.count();
        var eventRequest = new EventRequest(event.externalKey(), event.championshipId(),
                event.homeCompetitor().id(), event.awayCompetitor().id(), "Changed outside Demo", null, null,
                null, null, event.startsAt(), event.predictionClosesAt(), event.status(), event.format(),
                event.bestOf(), false, true, List.of());
        var marketRequest = new MarketRequest(event.id(), "EXTERNAL_EDIT", "Changed outside Demo", MarketStatus.OPEN, 10,
                market.options().stream().map(option -> new MarketOptionRequest(option.key(), option.label(),
                        option.multiplier(), option.active())).toList());
        List<MockHttpServletRequestBuilder> mutations = List.of(
                put("/api/admin/championships/" + event.championshipId()).content(json.writeValueAsString(
                        new ChampionshipRequest(event.sport().id(), "Changed outside Demo", "changed-demo", "DEMO",
                                ChampionshipStatus.ACTIVE, null, null, null))),
                put("/api/admin/competitors/" + event.homeCompetitor().id()).content(json.writeValueAsString(
                        new CompetitorRequest(event.sport().id(), "Changed outside Demo", event.homeCompetitor().code(),
                                null, null, false))),
                put("/api/admin/events/" + event.id()).content(json.writeValueAsString(eventRequest)),
                post("/api/admin/events").content(json.writeValueAsString(new EventRequest("unauthorized-demo-round",
                        event.championshipId(), event.homeCompetitor().id(), event.awayCompetitor().id(), "Extra round", null,
                        null, null, null, event.startsAt(), event.predictionClosesAt(), event.status(), event.format(),
                        event.bestOf(), false, true, List.of()))),
                post("/api/admin/events/" + event.id() + "/markets/generate"),
                put("/api/admin/events/" + event.id() + "/result").content("{\"homeScore\":2,\"awayScore\":1,\"finishEvent\":true,\"settleMarkets\":true}"),
                put("/api/admin/events/" + event.id() + "/classification").content(json.writeValueAsString(
                        new EventClassificationRequest(List.of(new EventParticipantRequest(event.homeCompetitor().id(), 0, 1, null)), true))),
                post("/api/admin/events/" + event.id() + "/cancel"),
                post("/api/admin/markets").content(json.writeValueAsString(marketRequest)),
                put("/api/admin/markets/" + market.id()).content(json.writeValueAsString(marketRequest)),
                patch("/api/admin/markets/" + market.id() + "/status").content("{\"status\":\"SUSPENDED\"}"),
                post("/api/admin/markets/" + market.id() + "/settle").content(json.writeValueAsString(
                        new SettleMarketRequest(market.options().getFirst().key()))),
                post("/api/admin/markets/" + market.id() + "/cancel"));
        for (var mutation : mutations) {
            http.perform(mutation.header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict());
        }
        http.perform(get("/api/admin/events").param("search", event.externalKey()).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].demoManaged").value(true));
        http.perform(get("/api/admin/championships").param("search", scenario.championship().name()).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].demoManaged").value(true));
        em.flush(); em.clear();
        assertThat(events.count()).isEqualTo(eventCount); assertThat(markets.count()).isEqualTo(marketCount);
        assertThat(ledger.count()).isEqualTo(entries);
        var unchanged = demo.scenario(player());
        assertThat(unchanged.event().status()).isEqualTo(event.status());
        assertThat(unchanged.event().title()).isEqualTo(event.title());
        assertThat(unchanged.event().homeCompetitor()).isEqualTo(event.homeCompetitor());
        assertThat(unchanged.event().markets()).isEqualTo(event.markets());
        assertThat(unchanged.ranking()).isEqualTo(scenario.ranking());
        assertThat(unchanged.championship()).isEqualTo(scenario.championship());
    }

    @Test void predictionPersistsAndFinalResultUsesExistingSettlementWalletAndRankingExactlyOnce() {
        var initial = demo.scenario(player());
        var placed = place(initial.event(), "2_1");
        em.flush(); em.clear();
        assertThat(predictions.findById(placed.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.ACTIVE);
        assertThat(demo.scenario(player()).predictions()).extracting(PredictionResponse::id).contains(placed.id());
        demo.start(initial.event().id(), admin());
        assertThatThrownBy(() -> place(initial.event(), "2_1")).isInstanceOf(ArenaProblem.RuleViolation.class);
        assertThatThrownBy(() -> commands.cancel(placed.id(), player())).isInstanceOf(ArenaProblem.RuleViolation.class);
        var finished = demo.finish(initial.event().id(), new DemoScenarioDtos.Result(2, 1), admin());
        em.flush(); em.clear();
        var persisted = predictions.findById(placed.id()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(PredictionStatus.WON);
        assertThat(persisted.getRewardedPoints()).isEqualTo(placed.potentialPoints());
        assertThat(events.findById(initial.event().id()).orElseThrow().getHomeScore()).isEqualTo(2);
        assertThat(finished.event().status()).isEqualTo(EventStatus.FINISHED);
        assertThat(demo.scenario(player()).ranking()).anySatisfy(row -> {
            assertThat(row.userId()).isEqualTo(player().getId());
            assertThat(row.points()).isEqualTo(placed.potentialPoints());
            assertThat(row.position()).isEqualTo(1);
        });
        long balance = wallets.wallet(player()).balance(), entries = ledger.count();
        demo.finish(initial.event().id(), new DemoScenarioDtos.Result(2, 1), admin());
        assertThat(wallets.wallet(player()).balance()).isEqualTo(balance);
        assertThat(ledger.count()).isEqualTo(entries);
        assertThatThrownBy(() -> demo.finish(initial.event().id(), new DemoScenarioDtos.Result(2, 0), admin()))
                .isInstanceOf(ArenaProblem.Conflict.class);
        assertThatThrownBy(() -> demo.start(initial.event().id(), admin())).isInstanceOf(ArenaProblem.Conflict.class);
    }

    @Test void resetArchivesOnlyCurrentDemoRoundAndLeavesOfficialEventsAndRegularAccountsUntouched() {
        ArenaEvent official = officialEvent();
        User regular = RegularTestUsers.participant(users);
        long regularBalance = wallets.wallet(regular).balance();
        long usersBefore = users.count();
        var first = demo.scenario(player());
        var placed = place(first.event(), "2_1");
        var reset = demo.reset(first.generation(), admin());
        em.flush(); em.clear();
        assertThat(events.findById(first.event().id()).orElseThrow().isDemoArchived()).isTrue();
        assertThat(predictions.findById(placed.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.REFUNDED);
        assertThat(reset.event().id()).isNotEqualTo(first.event().id());
        assertThat(reset.event().status()).isEqualTo(EventStatus.OPEN_FOR_PREDICTIONS);
        assertThat(reset.event().homeScore()).isNull();
        assertThat(demo.scenario(player()).predictions()).extracting(PredictionResponse::id).doesNotContain(placed.id());
        assertThat(demo.scenario(player()).ranking()).isEqualTo(first.ranking());
        var unchanged = events.findById(official.getId()).orElseThrow();
        assertThat(unchanged.getExternalProvider()).isEqualTo("PANDASCORE");
        assertThat(unchanged.getStatus()).isEqualTo(EventStatus.SCHEDULED);
        assertThat(unchanged.isDemoArchived()).isFalse();
        assertThat(wallets.wallet(regular).balance()).isEqualTo(regularBalance);
        assertThat(users.count()).isEqualTo(usersBefore);
        assertThat(place(reset.event(), "2_1").status()).isEqualTo(PredictionStatus.ACTIVE);
        long count = events.count(), entries = ledger.count();
        assertThat(demo.reset(first.generation(), admin()).event().id()).isEqualTo(reset.event().id());
        assertThat(events.count()).isEqualTo(count); assertThat(ledger.count()).isEqualTo(entries);
        assertThatThrownBy(() -> demo.finish(first.event().id(), new DemoScenarioDtos.Result(2, 1), admin()))
                .isInstanceOf(ArenaProblem.Conflict.class);
    }

    @Test void finalizedPredictionAndLedgerSurviveResetAsAnAuditableArchivedRound() {
        var first = demo.scenario(player());
        var placed = place(first.event(), "2_1");
        demo.finish(first.event().id(), new DemoScenarioDtos.Result(2, 1), admin());
        var credit = ledger.findByIdempotencyKey("prediction-win:" + placed.id()).orElseThrow();
        demo.reset(first.generation(), admin()); em.flush(); em.clear();
        assertThat(predictions.findById(placed.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.WON);
        assertThat(ledger.findById(credit.getId())).isPresent();
        assertThat(demo.scenario(player()).ranking()).isEqualTo(first.ranking());
    }

    @Test void suppliedIdsCannotMakeDemoActionsTouchOfficialOrLegacyMatches() {
        var official = officialEvent();
        var legacy = events.findByExternalKey("demo-cs2-open").orElseThrow();
        for (Long id : List.of(official.getId(), legacy.getId())) {
            assertThatThrownBy(() -> demo.start(id, admin())).isInstanceOf(ArenaProblem.Conflict.class);
            assertThatThrownBy(() -> demo.finish(id, new DemoScenarioDtos.Result(2, 1), admin())).isInstanceOf(ArenaProblem.Conflict.class);
        }
        var current = demo.scenario(player());
        assertThatThrownBy(() -> demo.start(current.event().id(), player())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> demo.reset(current.generation(), player())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> demo.reset(current.generation(), RegularTestUsers.admin(users))).isInstanceOf(AccessDeniedException.class);
    }

    @Test void demoParticipantCannotUseRealMarketsAndNormalParticipantCannotUseControlledCompetition() {
        var legacy = officialEvent();
        assertThatThrownBy(() -> commands.place(new PlacePredictionRequest(legacy.getId(), 1L, 1L, 10, null, "outside"), null, player()))
                .isInstanceOf(AccessDeniedException.class);
        var scenario = demo.scenario(player());
        var selection = scenario.event().markets().getFirst();
        assertThatThrownBy(() -> commands.place(new PlacePredictionRequest(scenario.event().id(), selection.id(), selection.options().getFirst().id(), 10, null, "normal-in-demo"), null, RegularTestUsers.participant(users)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void httpConfirmsRolesAndRejectsInvalidSeriesWithoutPersistingPartialResult() throws Exception {
        var scenario = demo.scenario(player());
        long balance = wallets.wallet(player()).balance(), entries = ledger.count();
        String path = "/api/demo/events/" + scenario.event().id() + "/result";
        http.perform(post(path).header("Authorization", "Bearer " + jwt.generate(player()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"homeScore\":2,\"awayScore\":1}"))
                .andExpect(status().isForbidden());
        http.perform(post(path).header("Authorization", "Bearer " + jwt.generate(admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"homeScore\":2,\"awayScore\":2}"))
                .andExpect(status().isUnprocessableEntity());
        http.perform(get("/api/demo/scenario").header("Authorization", "Bearer " + jwt.generate(player())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canManage").value(false));
        // Read through a new service transaction: the invalid result must roll back its implicit start,
        // closed markets and audit/settlement side effects, not just leave the test transaction uncommitted.
        var unchanged = demo.scenario(player());
        assertThat(unchanged.generation()).isEqualTo(scenario.generation());
        assertThat(unchanged.event().status()).isEqualTo(scenario.event().status());
        assertThat(unchanged.event().homeScore()).isEqualTo(scenario.event().homeScore());
        assertThat(unchanged.event().awayScore()).isEqualTo(scenario.event().awayScore());
        assertThat(unchanged.event().resultProcessedAt()).isEqualTo(scenario.event().resultProcessedAt());
        assertThat(unchanged.event().markets()).isEqualTo(scenario.event().markets());
        assertThat(unchanged.ranking()).isEqualTo(scenario.ranking());
        assertThat(wallets.wallet(player()).balance()).isEqualTo(balance);
        assertThat(ledger.count()).isEqualTo(entries);
    }

    @Test void liveTimeoutRefundsTheDemoAndNeverFabricatesAFinalScore() {
        var scenario = demo.scenario(player());
        var placed = place(scenario.event(), "2_1");
        demo.start(scenario.event().id(), admin());
        events.findById(scenario.event().id()).orElseThrow().setStartsAt(Instant.now().minusSeconds(3600));
        demo.maintain();
        var after = demo.scenario(player());
        assertThat(after.event().status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(after.event().homeScore()).isNull();
        assertThat(predictions.findById(placed.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.REFUNDED);
    }

    @Test void readAndRepeatedInitializationNeverResetAParticipantOrCreditPoints() {
        var scenario = demo.scenario(player());
        var placed = place(scenario.event(), "2_1");
        long balance = wallets.wallet(player()).balance(), entries = ledger.count();
        demo.initialize(); demo.scenario(player()); demo.scenario(admin());
        assertThat(demo.scenario(player()).event().id()).isEqualTo(scenario.event().id());
        assertThat(predictions.findById(placed.id())).isPresent();
        assertThat(wallets.wallet(player()).balance()).isEqualTo(balance); assertThat(ledger.count()).isEqualTo(entries);
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentResetWithTheSameGenerationCreatesExactlyOneNewRound() throws Exception {
        User administrator = admin();
        var before = demo.scenario(administrator);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<DemoScenarioDtos.Scenario> reset = () -> { gate.await(); return demo.reset(before.generation(), administrator); };
            var first = executor.submit(reset); var second = executor.submit(reset); gate.countDown();
            var a = first.get(30, TimeUnit.SECONDS); var b = second.get(30, TimeUnit.SECONDS);
            assertThat(a.generation()).isEqualTo(before.generation() + 1);
            assertThat(b.event().id()).isEqualTo(a.event().id());
        }
    }

    private PredictionResponse place(EventResponse event, String key) {
        var market = event.markets().stream().filter(m -> "SERIES_SCORE".equals(m.templateCode())).findFirst().orElseThrow();
        var option = market.options().stream().filter(o -> key.equals(o.key())).findFirst().orElseThrow();
        return commands.place(new PlacePredictionRequest(event.id(), market.id(), option.id(), 10, null, UUID.randomUUID().toString()), null, player());
    }
    private User player() { return users.findByEmailIgnoreCase("jogador@arenapredict.com").orElseThrow(); }
    private User admin() { return users.findByEmailIgnoreCase("admin@arenapredict.com").orElseThrow(); }
    private ArenaEvent officialEvent() {
        var sport = events.findByExternalKey("demo-cs2-open").orElseThrow().getChampionship().getSport();
        String id = UUID.randomUUID().toString();
        var championship = new Championship(); championship.setSport(sport); championship.setName("Official fixture");
        championship.setSlug("official-" + id); championship.setExternalProvider("PANDASCORE"); championship.setExternalId(id);
        var event = new ArenaEvent(); event.setChampionship(championships.save(championship));
        event.setTitle("Official fixture"); event.setExternalKey("official-" + id); event.setExternalProvider("PANDASCORE"); event.setExternalId(id);
        event.setStatus(EventStatus.SCHEDULED); event.setFormat(EventFormat.BO3); event.setBestOf(3);
        event.setStartsAt(Instant.now().plusSeconds(7200)); event.setPredictionClosesAt(Instant.now().plusSeconds(7100));
        return events.saveAndFlush(event);
    }
}
