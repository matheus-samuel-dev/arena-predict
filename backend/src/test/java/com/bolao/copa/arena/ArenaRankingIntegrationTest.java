package com.bolao.copa.arena;

import static com.bolao.copa.arena.api.ArenaDtos.PlacePredictionRequest;
import static com.bolao.copa.arena.domain.ArenaEnums.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.ArenaPoolRankingService;
import com.bolao.copa.arena.service.ArenaPredictionService;
import com.bolao.copa.arena.service.PointWalletService;
import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import com.bolao.copa.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class ArenaRankingIntegrationTest {
    @Autowired UserRepository users;
    @Autowired SportRepository sports;
    @Autowired ChampionshipRepository championships;
    @Autowired CompetitorRepository competitors;
    @Autowired ArenaEventRepository events;
    @Autowired EventParticipantRepository eventParticipants;
    @Autowired PredictionMarketRepository markets;
    @Autowired MarketOptionRepository options;
    @Autowired ArenaPredictionService predictionService;
    @Autowired ArenaPoolRankingService rankingService;
    @Autowired PointWalletService wallets;

    @Test
    @Transactional
    void racingHistoryUsesClassificationWithoutChangingSettledComparison() {
        var event=events.findByExternalKey("demo-ranking-v2-motorsport-week").orElseThrow();
        assertThat(event.getFormat()).isEqualTo(EventFormat.RACE);
        assertThat(event.getHomeScore()).isNull();
        assertThat(event.getAwayScore()).isNull();
        assertThat(eventParticipants.findByEventOrderByDisplayOrderAsc(event)).extracting(EventParticipant::getPosition).containsExactly(1,2);
        assertThat(markets.findByEventOrderByIdAsc(event)).singleElement().satisfies(market -> {
            assertThat(market.getStatus()).isEqualTo(MarketStatus.SETTLED);
            assertThat(market.getResultOptionKey()).isEqualTo("HOME");
        });
    }

    @Test
    @Transactional
    void demoRankingHasCoherentPeriodsOrderingAccuracyAndSportFilters() {
        User current = users.findByEmail("jogador@arenapredict.com").orElseThrow();

        var weekly = rankingService.ranking(current, RankingPeriod.WEEKLY, RankingScope.GLOBAL, null);
        assertThat(weekly).hasSize(8);
        assertThat(weekly).extracting(row -> row.playerName()).doesNotContain("Administrador Demo");
        assertThat(weekly).extracting(row -> row.points()).isSortedAccordingTo((left, right) -> Long.compare(right, left));
        assertThat(weekly).extracting(row -> row.points()).doesNotHaveDuplicates();

        var ana = weekly.stream().filter(row -> row.playerName().equals("Ana Ribeiro")).findFirst().orElseThrow();
        assertThat(ana.position()).isEqualTo(1);
        assertThat(ana.correctPredictions()).isEqualTo(5);
        assertThat(ana.totalPredictions()).isEqualTo(6);
        assertThat(ana.accuracy()).isEqualTo(83.33);
        assertThat(ana.streak()).isEqualTo(5);

        var beatriz = weekly.stream().filter(row -> row.playerName().equals("Beatriz Nunes")).findFirst().orElseThrow();
        assertThat(beatriz.correctPredictions()).isEqualTo(4);
        assertThat(beatriz.totalPredictions()).isEqualTo(6);
        assertThat(beatriz.accuracy()).isEqualTo(66.67);
        assertThat(beatriz.streak()).isEqualTo(3);

        var monthly = rankingService.ranking(current, RankingPeriod.MONTHLY, RankingScope.GLOBAL, null);
        var overall = rankingService.ranking(current, RankingPeriod.ALL, RankingScope.GLOBAL, null);
        assertThat(monthly).allMatch(row -> row.totalPredictions() == 8);
        assertThat(overall).allMatch(row -> row.totalPredictions() >= 9);

        var cs2 = rankingService.ranking(current, RankingPeriod.WEEKLY, RankingScope.GLOBAL, "CS2");
        assertThat(cs2).hasSize(8).allMatch(row -> row.totalPredictions() == 1);
        assertThat(rankingService.ranking(current, RankingPeriod.WEEKLY, RankingScope.GLOBAL, "Tênis"))
                .hasSize(8).allMatch(row -> row.totalPredictions() == 1);
        assertThat(rankingService.ranking(current, RankingPeriod.WEEKLY, RankingScope.GLOBAL, "MOTORSPORT"))
                .hasSize(8).allMatch(row -> row.totalPredictions() == 1);
        assertThat(rankingService.ranking(current, RankingPeriod.WEEKLY, RankingScope.GLOBAL, null))
                .usingRecursiveComparison().isEqualTo(weekly);
    }

    @Test
    @Transactional
    void settlementChangesWalletAccuracyStreakAndRankingWithoutDuplicateReward() {
        User alpha = participant("Validação Alpha");
        User beta = participant("Validação Beta");
        wallets.wallet(alpha);
        wallets.wallet(beta);

        var first = settle(alpha, beta, 100, "HOME", "2.00", "1.80");
        long alphaAfterFirst = wallets.wallet(alpha).balance();
        assertThat(first.settlement().winners()).isEqualTo(1);
        assertThat(first.settlement().losers()).isEqualTo(1);
        assertThat(predictionNet(alpha, first.alphaPredictionId())).isEqualTo(100);
        assertThat(predictionNet(beta, first.betaPredictionId())).isEqualTo(-100);

        var repeated = predictionService.settleMarket(first.settlement().marketId(), "HOME");
        assertThat(repeated.alreadySettled()).isTrue();
        assertThat(wallets.wallet(alpha).balance()).isEqualTo(alphaAfterFirst);
        assertThat(wallets.transactions(alpha).stream()
                .filter(entry -> entry.type() == PointTransactionType.PREDICTION_WON)
                .filter(entry -> first.alphaPredictionId().toString().equals(entry.referenceId())))
                .hasSize(1);

        var second = settle(alpha, beta, 60, "AWAY", "2.00", "1.80");
        var ranking = rankingService.ranking(alpha, RankingPeriod.WEEKLY, RankingScope.GLOBAL, "FOOTBALL");
        var alphaRow = ranking.stream().filter(row -> row.playerName().equals(alpha.getName())).findFirst().orElseThrow();
        var betaRow = ranking.stream().filter(row -> row.playerName().equals(beta.getName())).findFirst().orElseThrow();

        assertThat(alphaRow.correctPredictions()).isEqualTo(1);
        assertThat(alphaRow.totalPredictions()).isEqualTo(2);
        assertThat(alphaRow.accuracy()).isEqualTo(50.0);
        assertThat(alphaRow.streak()).isZero();
        assertThat(alphaRow.points()).isEqualTo(200);
        assertThat(betaRow.correctPredictions()).isEqualTo(1);
        assertThat(betaRow.totalPredictions()).isEqualTo(2);
        assertThat(betaRow.accuracy()).isEqualTo(50.0);
        assertThat(betaRow.streak()).isEqualTo(1);
        assertThat(betaRow.points()).isEqualTo(108);
        assertThat(alphaRow.position()).isLessThan(betaRow.position());
        assertThat(predictionNet(alpha, first.alphaPredictionId(), second.alphaPredictionId())).isEqualTo(40);
        assertThat(predictionNet(beta, first.betaPredictionId(), second.betaPredictionId())).isEqualTo(-52);

        assertThat(rankingService.ranking(alpha, RankingPeriod.MONTHLY, RankingScope.GLOBAL, "FOOTBALL"))
                .anyMatch(row -> row.playerName().equals(alpha.getName()) && row.totalPredictions() == 2);
        assertThat(rankingService.ranking(alpha, RankingPeriod.ALL, RankingScope.GLOBAL, "FOOTBALL"))
                .anyMatch(row -> row.playerName().equals(beta.getName()) && row.totalPredictions() == 2);
        assertThat(rankingService.ranking(alpha, RankingPeriod.WEEKLY, RankingScope.GLOBAL, "TENNIS"))
                .noneMatch(row -> row.playerName().equals(alpha.getName()) || row.playerName().equals(beta.getName()));
    }

    private User participant(String name) {
        User user = new User();
        user.setName(name);
        user.setEmail("ranking-" + UUID.randomUUID() + "@arenapredict.test");
        user.setPasswordHash("não-utilizada-neste-teste");
        user.setRole(UserRole.PARTICIPANTE);
        return users.saveAndFlush(user);
    }

    private SettlementFixture settle(
            User alpha, User beta, int stake, String correctKey, String homeMultiplier, String awayMultiplier) {
        Sport sport = sports.findByCodeIgnoreCase("FOOTBALL").orElseThrow();
        Championship championship = championships.findBySportOrderByNameAsc(sport).getFirst();
        Competitor home = competitors.findBySportAndCodeIgnoreCase(sport, "PAL").orElseThrow();
        Competitor away = competitors.findBySportAndCodeIgnoreCase(sport, "FLA").orElseThrow();
        String suffix = UUID.randomUUID().toString();

        ArenaEvent event = new ArenaEvent();
        event.setExternalKey("ranking-rule-" + suffix);
        event.setChampionship(championship);
        event.setHomeCompetitor(home);
        event.setAwayCompetitor(away);
        event.setTitle("Validação funcional do ranking");
        event.setStartsAt(Instant.now().plusSeconds(7_200));
        event.setPredictionClosesAt(Instant.now().plusSeconds(3_600));
        event.setStatus(EventStatus.OPEN_FOR_PREDICTIONS);
        event.setFormat(EventFormat.STANDARD);
        event = events.saveAndFlush(event);
        addParticipant(event, home, 0);
        addParticipant(event, away, 1);

        PredictionMarket market = new PredictionMarket();
        market.setEvent(event);
        market.setCode("RESULT-" + suffix);
        market.setName("Vencedor");
        market.setStatus(MarketStatus.OPEN);
        market.setMinimumPoints(20);
        market = markets.saveAndFlush(market);
        MarketOption homeOption = option(market, "HOME", home.getName(), homeMultiplier);
        MarketOption awayOption = option(market, "AWAY", away.getName(), awayMultiplier);

        String key = "ranking-rule-" + suffix;
        var alphaPrediction = predictionService.place(new PlacePredictionRequest(event.getId(), market.getId(), homeOption.getId(), stake, null, key), key, alpha);
        var betaPrediction = predictionService.place(new PlacePredictionRequest(event.getId(), market.getId(), awayOption.getId(), stake, null, key), key, beta);
        event.setStatus(EventStatus.FINISHED);
        events.saveAndFlush(event);
        market.setStatus(MarketStatus.CLOSED);
        markets.saveAndFlush(market);
        return new SettlementFixture(predictionService.settleMarket(market.getId(), correctKey),
                alphaPrediction.id(), betaPrediction.id());
    }

    private void addParticipant(ArenaEvent event, Competitor competitor, int displayOrder) {
        EventParticipant participant = new EventParticipant();
        participant.setEvent(event);
        participant.setCompetitor(competitor);
        participant.setDisplayOrder(displayOrder);
        eventParticipants.save(participant);
    }

    private MarketOption option(PredictionMarket market, String key, String label, String multiplier) {
        MarketOption option = new MarketOption();
        option.setMarket(market);
        option.setKey(key);
        option.setLabel(label);
        option.setMultiplier(new BigDecimal(multiplier));
        return options.saveAndFlush(option);
    }

    private long predictionNet(User user, Long... predictionIds) {
        Set<String> references = Set.copyOf(List.of(predictionIds).stream().map(String::valueOf).toList());
        return wallets.transactions(user).stream()
                .filter(entry -> "PREDICTION".equals(entry.referenceType()))
                .filter(entry -> references.contains(entry.referenceId()))
                .filter(entry -> entry.type() == PointTransactionType.PREDICTION_PLACED
                        || entry.type() == PointTransactionType.PREDICTION_WON)
                .mapToLong(entry -> entry.amount())
                .sum();
    }

    private record SettlementFixture(com.bolao.copa.arena.api.ArenaDtos.SettlementResponse settlement,
                                     Long alphaPredictionId, Long betaPredictionId) { }
}
