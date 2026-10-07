package com.bolao.copa.arena;

import static com.bolao.copa.arena.api.ArenaDtos.*;
import static org.assertj.core.api.Assertions.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Real-match fixtures use the existing prediction, wallet and ranking services without HTTP calls. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExternalMatchSettlementIntegrationTest {
    @Autowired ArenaEventRepository events;
    @Autowired ChampionshipRepository championships;
    @Autowired CompetitorRepository competitors;
    @Autowired PredictionMarketRepository markets;
    @Autowired MarketOptionRepository options;
    @Autowired ArenaPredictionRepository predictions;
    @Autowired PointLedgerRepository ledger;
    @Autowired UserRepository users;
    @Autowired ArenaCatalogService catalog;
    @Autowired MarketTemplateService templates;
    @Autowired MarketAvailabilityService availability;
    @Autowired MarketSettlementEngine engine;
    @Autowired ArenaPredictionService commands;
    @Autowired AdminEventResultService adminResults;
    @Autowired PointWalletService wallets;
    @Autowired ArenaPoolRankingService rankings;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    private User participant;

    @org.junit.jupiter.api.BeforeEach
    void createIndependentParticipant() {
        participant = com.bolao.copa.support.RegularTestUsers.freshParticipant(users);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 5})
    void officialMatchesPublishOnlyIdempotentPreMatchSeriesMarkets(int bestOf) {
        var event = fixture(bestOf, true);
        var generated = templates.generate(event.getId());
        assertThat(generated).hasSize(bestOf == 1 ? 1 : 6);
        assertThat(generated).allSatisfy(market -> {
            assertThat(market.getTimingMode()).isEqualTo(MarketTimingMode.PRE_MATCH_ONLY);
            assertThat(market.getTemplateCode()).isIn("SERIES_WINNER", "TOTAL_MAPS", "MAP_HANDICAP", "SERIES_SCORE", "HOME_MAP", "AWAY_MAP");
            assertThat(catalog.marketResponse(market).pricingReason()).contains("não são odds");
        });
        assertThat(templates.generate(event.getId())).extracting(PredictionMarket::getId)
                .containsExactlyElementsOf(generated.stream().map(PredictionMarket::getId).toList());
        assertThat(markets.findByEventOrderByIdAsc(event)).hasSize(generated.size());
        assertThat(catalog.eventResponse(event.getId()).resultSchema()).isEmpty();
        event.setStatus(EventStatus.LIVE);
        assertThat(generated).allSatisfy(market -> assertThat(availability.evaluate(market).code()).isEqualTo("EVENT_STARTED"));
    }

    @Test
    void absentSeriesFormatAndScoresRemainNullAndDoNotPublishUnsettleableMarkets() {
        var event = fixture(null, true);
        assertThat(templates.generate(event.getId())).isEmpty();
        var response = catalog.eventResponse(event.getId());
        assertThat(response.bestOf()).isNull();
        assertThat(response.homeScore()).isNull();
        assertThat(response.awayScore()).isNull();
        assertThat(response.liveScoreAvailable()).isFalse();
        event.setHomeScore(2);
        event.setAwayScore(1);
        assertThatThrownBy(() -> engine.validateEvent(event, List.of(), true))
                .isInstanceOf(ArenaProblem.RuleViolation.class).hasMessageContaining("formato da série");
    }

    @Test
    void officialResultUsesExistingWalletAndRankingRulesAndPaysEachPredictionOnlyOnce() {
        var event = fixture(3, true);
        templates.generate(event.getId());
        var user = participant;
        var previousRanking = rankings.globalRanking(user).stream().filter(row -> row.userId().equals(user.getId())).findFirst();
        long beforePoints = previousRanking.map(RankingRow::points).orElse(0L);
        long beforeCorrect = previousRanking.map(RankingRow::correctPredictions).orElse(0L);
        long beforeTotal = previousRanking.map(RankingRow::totalPredictions).orElse(0L);
        long balance = wallets.wallet(user).balance();
        var winner = place(event, "SERIES_WINNER", "HOME", 40);
        var loser = place(event, "SERIES_SCORE", "0_2", 20);
        event.setStatus(EventStatus.LIVE);
        assertThat(catalog.eventResponse(event.getId()).availableMarketCount()).isZero();
        event.setHomeScore(2);
        event.setAwayScore(1);
        event.setStatus(EventStatus.FINISHED);
        commands.settleDerived(event);
        entityManager.flush();
        Long eventId = event.getId();
        entityManager.clear();
        event = events.findById(eventId).orElseThrow();
        commands.settleDerived(event);
        commands.settleDerived(event);

        assertThat(predictions.findById(winner.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.WON);
        assertThat(predictions.findById(loser.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.LOST);
        // This regular account is new: the existing progression rules also grant its first-prediction
        // and first-win achievements. They must remain independent of settlement retries.
        assertThat(progressionCredit(user.getId())).isEqualTo(300);
        assertThat(wallets.wallet(user).balance()).isEqualTo(balance - 60 + winner.potentialPoints() + 300);
        assertThat(ledger.findByIdempotencyKey("prediction-win:" + winner.id())).isPresent()
                .get().extracting(PointLedgerEntry::getAmount).isEqualTo((long) winner.potentialPoints());
        assertThat(rankings.globalRanking(user).stream().filter(row -> row.userId().equals(user.getId())))
                .singleElement().satisfies(row -> {
                    assertThat(row.points()).isEqualTo(beforePoints + winner.potentialPoints());
                    assertThat(row.correctPredictions()).isEqualTo(beforeCorrect + 1);
                    assertThat(row.totalPredictions()).isEqualTo(beforeTotal + 2);
                });
        assertThat(markets.findByEventOrderByIdAsc(event)).allMatch(m -> m.getStatus() == MarketStatus.SETTLED);
    }

    @Test
    void officialCancellationRefundsOnceThroughTheExistingBusinessRule() {
        var event = fixture(3, true);
        templates.generate(event.getId());
        var user = participant;
        long balance = wallets.wallet(user).balance();
        var prediction = place(event, "SERIES_WINNER", "HOME", 40);
        assertThat(commands.cancelEvent(event.getId())).isEqualTo(1);
        assertThat(commands.cancelEvent(event.getId())).isZero();
        assertThat(events.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(EventStatus.CANCELLED);
        assertThat(predictions.findById(prediction.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.REFUNDED);
        assertThat(progressionCredit(user.getId())).isEqualTo(100);
        assertThat(wallets.wallet(user).balance()).isEqualTo(balance + 100);
    }

    @Test
    void manualResultCannotAlterAnOfficialMatchEvenIfTheSameScoreIsReplayed() {
        var event = fixture(3, true);
        event.setHomeScore(2);
        event.setAwayScore(1);
        event.setStatus(EventStatus.FINISHED);
        assertThatThrownBy(() -> adminResults.record(event.getId(), new EventResultRequest(2, 1, true), "official-replay"))
                .isInstanceOf(ArenaProblem.Conflict.class).hasMessageContaining("provedor esportivo");
        assertThatThrownBy(() -> catalog.recordResult(event.getId(), new EventResultRequest(0, 2, true)))
                .isInstanceOf(ArenaProblem.Conflict.class).hasMessageContaining("provedor esportivo");
        assertThat(event.getHomeScore()).isEqualTo(2);
        assertThat(event.getAwayScore()).isEqualTo(1);
    }

    @Test
    void manualCancellationSettlementAndCustomMarketsCannotBypassProviderOwnership() {
        var event = fixture(3, true);
        var generated = templates.generate(event.getId());
        var market = generated.getFirst();
        assertThatThrownBy(() -> commands.cancelEventManually(event.getId()))
                .isInstanceOf(ArenaProblem.Conflict.class).hasMessageContaining("provedor esportivo");
        assertThatThrownBy(() -> commands.cancelMarket(market.getId()))
                .isInstanceOf(ArenaProblem.Conflict.class).hasMessageContaining("provedor esportivo");
        assertThatThrownBy(() -> commands.settleMarket(market.getId(), "HOME"))
                .isInstanceOf(ArenaProblem.Conflict.class).hasMessageContaining("provedor esportivo");
        assertThatThrownBy(() -> catalog.saveMarket(null,
                new MarketRequest(event.getId(), "UNSUPPORTED_MAP", "Mapa simulado", MarketStatus.OPEN, 10, List.of())))
                .isInstanceOf(ArenaProblem.Conflict.class).hasMessageContaining("publicados automaticamente");
        assertThat(event.getStatus()).isEqualTo(EventStatus.SCHEDULED);
        assertThat(market.getStatus()).isEqualTo(MarketStatus.OPEN);
    }

    @Test
    void reviewQuarantineBlocksNewPredictionsWithoutInventingAResult() {
        var event = fixture(3, true);
        var market = templates.generate(event.getId()).getFirst();
        event.setResultReviewRequired(true);
        assertThat(availability.evaluate(market).code()).isEqualTo("RESULT_REVIEW");
        assertThatThrownBy(() -> place(event, "SERIES_WINNER", "HOME", 20)).hasMessageContaining("Dados em revisão");
        assertThat(event.getHomeScore()).isNull();
        assertThat(event.getAwayScore()).isNull();
    }

    @Test
    void demoResultAndSimulatedMapMarketsContinueToWorkWithoutAProvider() {
        var event = fixture(3, false);
        event.setDemo(true);
        var generated = templates.generate(event.getId());
        assertThat(generated).anyMatch(m -> "PISTOL1".equals(m.getTemplateCode()));
        event.setStatus(EventStatus.LIVE);
        var response = adminResults.record(event.getId(), new EventResultRequest(2, 1, true,
                Map.of("map1Home", "13", "map1Away", "10", "map1Half1Home", "7", "map1Half1Away", "5",
                        "map1Half2Home", "6", "map1Half2Away", "5", "pistol1", "HOME"), true), "demo-simulation");
        assertThat(response.demo()).isTrue();
        assertThat(response.externalProvider()).isNull();
        assertThat(response.status()).isEqualTo(EventStatus.FINISHED);
        assertThat(response.markets()).allMatch(m -> m.status() == MarketStatus.SETTLED);
    }

    private long progressionCredit(Long userId) {
        return ledger.findAll().stream().filter(entry -> entry.getWallet().getUser().getId().equals(userId))
                .filter(entry -> entry.getType() == PointTransactionType.ACHIEVEMENT)
                .mapToLong(PointLedgerEntry::getAmount).sum();
    }

    private ArenaEvent fixture(Integer bestOf, boolean official) {
        // Official fixtures share only the modality with Demo, never the provider-owned catalog.
        var source = events.findByExternalKey("demo-cs2-open").orElseThrow();
        var event = new ArenaEvent();
        var identity = UUID.randomUUID().toString();
        event.setExternalKey("test-official-settlement-" + identity);
        event.setTitle("CS2 integration fixture");
        event.setChampionship(source.getChampionship());
        event.setHomeCompetitor(source.getHomeCompetitor());
        event.setAwayCompetitor(source.getAwayCompetitor());
        event.setBestOf(bestOf);
        event.setFormat(bestOf == null ? EventFormat.STANDARD : bestOf == 1 ? EventFormat.BO1 : bestOf == 5 ? EventFormat.BO5 : EventFormat.BO3);
        event.setStartsAt(Instant.now().plusSeconds(3600));
        event.setPredictionClosesAt(Instant.now().plusSeconds(3500));
        if (official) {
            event.setExternalProvider("PANDASCORE");
            event.setExternalId(identity);
            var championship = new Championship();
            championship.setSport(source.getChampionship().getSport());
            championship.setName("Official tournament fixture");
            championship.setSlug("official-" + identity);
            championship.setSeason("2026");
            championship.setExternalProvider("PANDASCORE");
            championship.setExternalId(identity);
            event.setChampionship(championships.save(championship));
            event.setHomeCompetitor(officialTeam(source.getHomeCompetitor(), "H" + identity.substring(0, 20)));
            event.setAwayCompetitor(officialTeam(source.getAwayCompetitor(), "A" + identity.substring(0, 20)));
        }
        return events.save(event);
    }

    private Competitor officialTeam(Competitor source, String identity) {
        var team = new Competitor();
        team.setSport(source.getSport());
        team.setName(source.getName());
        team.setCode(identity);
        team.setExternalProvider("PANDASCORE");
        team.setExternalId(identity);
        return competitors.save(team);
    }

    private PredictionResponse place(ArenaEvent event, String template, String selection, int stake) {
        var market = markets.findByEventOrderByIdAsc(event).stream().filter(m -> template.equals(m.getTemplateCode())).findFirst().orElseThrow();
        var option = options.findByMarketAndKey(market, selection).orElseThrow();
        var user = participant;
        String key = UUID.randomUUID().toString();
        return commands.place(new PlacePredictionRequest(event.getId(), market.getId(), option.getId(), stake, null, key), key, user);
    }
}
