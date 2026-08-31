package com.bolao.copa.arena;

import static com.bolao.copa.arena.api.ArenaDtos.*;
import static org.assertj.core.api.Assertions.*;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.domain.ArenaEnums.MarketStatus;
import com.bolao.copa.arena.domain.ArenaEnums.PointTransactionType;
import com.bolao.copa.arena.domain.ArenaEnums.PredictionStatus;
import com.bolao.copa.arena.config.ArenaDemoInitializer;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.repository.UserRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class ArenaPredictionIntegrationTest {
    @Autowired UserRepository users;
    @Autowired ArenaEventRepository events;
    @Autowired CompetitorRepository competitors;
    @Autowired PredictionMarketRepository markets;
    @Autowired MarketOptionRepository options;
    @Autowired ArenaPredictionRepository predictions;
    @Autowired ArenaPredictionService predictionService;
    @Autowired PointWalletService walletService;
    @Autowired ArenaCatalogService catalogService;
    @Autowired DemoLiveEventService liveEventService;
    @Autowired AdminAuditRepository audits;
    @Autowired ArenaDemoInitializer demoInitializer;

    @Test
    void contextStartsWithVersionedGenericDemoData() {
        assertThat(events.findByExternalKey("demo-football-live")).isPresent();
        assertThat(events.findByExternalKey("demo-cs2-live")).isPresent();
        assertThat(markets.countByStatus(MarketStatus.OPEN)).isPositive();
        assertThat(users.findByEmailIgnoreCase("admin@bolao.com")).isEmpty();
        assertThat(users.findByEmailIgnoreCase("user@bolao.com")).isEmpty();
        assertThat(users.findByEmailIgnoreCase("marina.costa@arenapredict.com")).isPresent();
        assertThat(users.findByEmailIgnoreCase("rafael.lima@arenapredict.com")).isPresent();
        assertThat(users.findByEmailIgnoreCase("beatriz.nunes@arenapredict.com")).isPresent();
        assertThat(users.findByEmailIgnoreCase("camila.rocha@arenapredict.com")).isPresent();
        assertThat(users.findByEmailIgnoreCase("lucas.almeida@arenapredict.com")).isPresent();
        assertThat(users.findByEmailIgnoreCase("ana.ribeiro@arenapredict.com")).isPresent();
        assertThat(users.findByEmailIgnoreCase("diego.ferreira@arenapredict.com")).isPresent();
        assertThat(competitors.findAll())
                .filteredOn(value -> "FURIA".equals(value.getCode()) || "NAVI".equals(value.getCode()))
                .hasSize(2)
                .allMatch(value -> value.getImageUrl() != null && !value.getImageUrl().isBlank());
        var cancelled = events.findByExternalKey("demo-football-cancelled").orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(EventStatus.CANCELLED);
        assertThat(predictions.findByEventAndStatus(cancelled, PredictionStatus.REFUNDED)).isNotEmpty();
    }

    @Test
    @Transactional
    void placingPredictionDebitsVirtualPointsAndIsIdempotent() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var event = events.findByExternalKey("demo-tennis-open").orElseThrow();
        var market = markets.findByEventOrderByIdAsc(event).getFirst();
        var option = options.findByMarketOrderByIdAsc(market).getFirst();
        long before = walletService.wallet(user).balance();
        String key = "test-" + UUID.randomUUID();
        var request = new PlacePredictionRequest(event.getId(), market.getId(), option.getId(), 75, null, key);

        var first = predictionService.place(request, key, user);
        long afterFirst = walletService.wallet(user).balance();
        var repeated = predictionService.place(request, key, user);

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(afterFirst).isGreaterThanOrEqualTo(before - 75); // progress rewards may unlock atomically
        assertThat(walletService.wallet(user).balance()).isEqualTo(afterFirst);
        assertThat(walletService.transactions(user)).anyMatch(entry -> entry.amount() == -75
                && "PREDICTION".equals(entry.referenceType()) && first.id().toString().equals(entry.referenceId()));
        assertThat(predictions.findByIdempotencyKey("prediction:user:" + user.getId() + ":" + key)).isPresent();
    }

    @Test
    void insufficientBalanceRollsBackPredictionAndLedger() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var event = events.findByExternalKey("demo-lol-open").orElseThrow();
        var market = markets.findByEventOrderByIdAsc(event).getFirst();
        var option = options.findByMarketOrderByIdAsc(market).getFirst();
        String key = "insufficient-" + UUID.randomUUID();
        int impossible = Math.toIntExact(walletService.wallet(user).balance() + 1);

        assertThatThrownBy(() -> predictionService.place(
                new PlacePredictionRequest(event.getId(), market.getId(), option.getId(), impossible, null, key), key, user))
                .isInstanceOf(ArenaProblem.RuleViolation.class)
                .hasMessageContaining("Saldo");
        assertThat(predictions.findByIdempotencyKey("prediction:user:" + user.getId() + ":" + key)).isEmpty();
    }

    @Test
    @Transactional
    void settlingSameMarketTwiceDoesNotRewardTwice() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var event = events.findByExternalKey("demo-tennis-open").orElseThrow();
        var market = markets.findByEventOrderByIdAsc(event).getFirst();
        var option = options.findByMarketOrderByIdAsc(market).getFirst();
        String key = "settle-" + UUID.randomUUID();
        predictionService.place(new PlacePredictionRequest(event.getId(), market.getId(), option.getId(), 50, null, key), key, user);

        event.setStatus(EventStatus.FINISHED);
        events.saveAndFlush(event);
        market.setStatus(MarketStatus.CLOSED);
        markets.saveAndFlush(market);

        var first = predictionService.settleMarket(market.getId(), option.getKey());
        long afterFirst = walletService.wallet(user).balance();
        var repeated = predictionService.settleMarket(market.getId(), option.getKey());

        assertThat(first.winners()).isPositive();
        assertThat(repeated.alreadySettled()).isTrue();
        assertThat(walletService.wallet(user).balance()).isEqualTo(afterFirst);
        assertThat(audits.findAll()).anyMatch(entry -> "MARKET_SETTLED".equals(entry.getAction())
                && market.getId().toString().equals(entry.getResourceId()));
    }

    @Test
    void settlementRequiresFinishedEventAndClosedMarket() {
        var event = events.findByExternalKey("demo-lol-open").orElseThrow();
        var market = markets.findByEventOrderByIdAsc(event).getFirst();
        var option = options.findByMarketOrderByIdAsc(market).getFirst();

        assertThatThrownBy(() -> predictionService.settleMarket(market.getId(), option.getKey()))
                .isInstanceOf(ArenaProblem.RuleViolation.class)
                .hasMessageContaining("Finalize o evento");
    }

    @Test
    @Transactional
    void refundRestoresBalanceWithoutIncreasingLifetimeEarningsOrXp() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var event = events.findByExternalKey("demo-tennis-open").orElseThrow();
        var market = markets.findByEventOrderByIdAsc(event).getFirst();
        var option = options.findByMarketOrderByIdAsc(market).getFirst();
        String key = "refund-xp-" + UUID.randomUUID();

        var placed = predictionService.place(
                new PlacePredictionRequest(event.getId(), market.getId(), option.getId(), 75, null, key), key, user);
        var afterPlacement = walletService.wallet(user);
        var cancelled = predictionService.cancel(placed.id(), user);
        var afterRefund = walletService.wallet(user);

        assertThat(cancelled.status()).isEqualTo(PredictionStatus.CANCELLED);
        assertThat(afterRefund.balance()).isEqualTo(afterPlacement.balance() + 75);
        assertThat(afterRefund.lifetimeEarned()).isEqualTo(afterPlacement.lifetimeEarned());
    }

    @Test
    void concurrentCancellationRefundsExactlyOnce() throws Exception {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var event = events.findByExternalKey("demo-tennis-open").orElseThrow();
        var market = markets.findByEventOrderByIdAsc(event).getFirst();
        var option = options.findByMarketOrderByIdAsc(market).getFirst();
        String key = "cancel-race-" + UUID.randomUUID();
        var placed = predictionService.place(
                new PlacePredictionRequest(event.getId(), market.getId(), option.getId(), 83, null, key), key, user);
        long balanceAfterPlacement = walletService.wallet(user).balance();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return predictionService.cancel(placed.id(), users.findById(user.getId()).orElseThrow());
            });
            var second = executor.submit(() -> {
                start.await();
                return predictionService.cancel(placed.id(), users.findById(user.getId()).orElseThrow());
            });
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(PredictionStatus.CANCELLED);
            assertThat(second.get(10, TimeUnit.SECONDS).status()).isEqualTo(PredictionStatus.CANCELLED);
        } finally {
            executor.shutdownNow();
        }

        var reloadedUser = users.findById(user.getId()).orElseThrow();
        assertThat(walletService.wallet(reloadedUser).balance()).isEqualTo(balanceAfterPlacement + 83);
        assertThat(walletService.transactions(reloadedUser).stream()
                .filter(entry -> entry.type() == PointTransactionType.REFUND
                        && "PREDICTION".equals(entry.referenceType())
                        && entry.referenceId().equals(placed.id().toString()) && entry.amount() == 83)
                .count()).isEqualTo(1);
    }

    @Test
    @Transactional
    void cancellationIsUnavailableAfterEventLeavesPredictionPhase() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var event = events.findByExternalKey("demo-tennis-open").orElseThrow();
        var market = markets.findByEventOrderByIdAsc(event).getFirst();
        var option = options.findByMarketOrderByIdAsc(market).getFirst();
        String key = "late-cancel-" + UUID.randomUUID();
        var placed = predictionService.place(
                new PlacePredictionRequest(event.getId(), market.getId(), option.getId(), 61, null, key), key, user);

        event.setStatus(EventStatus.LIVE);
        events.saveAndFlush(event);

        assertThat(predictionService.list(user)).filteredOn(value -> value.id().equals(placed.id()))
                .singleElement().extracting(PredictionResponse::canCancel).isEqualTo(false);
        assertThatThrownBy(() -> predictionService.cancel(placed.id(), user))
                .isInstanceOf(ArenaProblem.RuleViolation.class)
                .hasMessageContaining("prazo");
    }

    @Test
    @Transactional
    void reusedIdempotencyKeyWithDifferentPayloadIsRejected() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var event = events.findByExternalKey("demo-tennis-open").orElseThrow();
        var market = markets.findByEventOrderByIdAsc(event).getFirst();
        var option = options.findByMarketOrderByIdAsc(market).getFirst();
        String key = "payload-" + UUID.randomUUID();
        predictionService.place(new PlacePredictionRequest(event.getId(), market.getId(), option.getId(), 50, null, key), key, user);

        assertThatThrownBy(() -> predictionService.place(
                new PlacePredictionRequest(event.getId(), market.getId(), option.getId(), 51, null, key), key, user))
                .isInstanceOf(ArenaProblem.Conflict.class)
                .hasMessageContaining("dados diferentes");
    }

    @Test
    @Transactional
    void dedicatedMarketCancellationRefundsActivePredictionsAndIsAudited() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var event = events.findByExternalKey("demo-tennis-open").orElseThrow();
        var market = markets.findByEventOrderByIdAsc(event).getFirst();
        var option = options.findByMarketOrderByIdAsc(market).getFirst();
        String key = "market-cancel-" + UUID.randomUUID();
        predictionService.place(new PlacePredictionRequest(event.getId(), market.getId(), option.getId(), 50, null, key), key, user);
        long earnedBeforeRefund = walletService.wallet(user).lifetimeEarned();

        int refunded = predictionService.cancelMarket(market.getId());

        assertThat(refunded).isPositive();
        assertThat(markets.findById(market.getId()).orElseThrow().getStatus()).isEqualTo(MarketStatus.CANCELLED);
        assertThat(walletService.wallet(user).lifetimeEarned()).isEqualTo(earnedBeforeRefund);
        assertThat(audits.findAll()).anyMatch(entry -> "MARKET_CANCELLED".equals(entry.getAction())
                && market.getId().toString().equals(entry.getResourceId()));
    }

    @Test
    @Transactional
    void unsafeGenericMarketStatusesAndOversizedStakeAreRejected() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var event = events.findByExternalKey("demo-lol-open").orElseThrow();
        var market = markets.findByEventOrderByIdAsc(event).getFirst();
        var option = options.findByMarketOrderByIdAsc(market).getFirst();

        assertThatThrownBy(() -> catalogService.changeMarketStatus(market.getId(), MarketStatus.SETTLED))
                .isInstanceOf(ArenaProblem.RuleViolation.class);
        assertThatThrownBy(() -> catalogService.changeMarketStatus(market.getId(), MarketStatus.CANCELLED))
                .isInstanceOf(ArenaProblem.RuleViolation.class);
        assertThatThrownBy(() -> predictionService.place(new PlacePredictionRequest(event.getId(), market.getId(),
                option.getId(), MAX_PREDICTION_STAKE_POINTS + 1, null, "too-large"), "too-large", user))
                .isInstanceOf(ArenaProblem.RuleViolation.class)
                .hasMessageContaining("máximo");
    }

    @Test
    @Transactional
    void terminalEventsCannotBeCancelledOrReceiveNewResults() {
        var settled = events.findByExternalKey("demo-football-settled").orElseThrow();
        var cancelled = events.findByExternalKey("demo-football-cancelled").orElseThrow();

        assertThatThrownBy(() -> predictionService.cancelEvent(settled.getId()))
                .isInstanceOf(ArenaProblem.Conflict.class);
        assertThatThrownBy(() -> catalogService.recordResult(cancelled.getId(), new EventResultRequest(0, 0, true)))
                .isInstanceOf(ArenaProblem.Conflict.class);
    }

    @Test
    @Transactional
    void demoProviderDoesNotResurrectTerminalEvent() {
        var event = events.findByExternalKey("demo-football-live").orElseThrow();
        event.setStatus(EventStatus.CANCELLED);
        events.saveAndFlush(event);

        liveEventService.refresh();

        assertThat(events.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    @Transactional
    void demoSeedDoesNotRewriteExistingWorkflowStateOrSchedule() {
        var event = events.findByExternalKey("demo-lol-open").orElseThrow();
        var market = markets.findByEventOrderByIdAsc(event).getFirst();
        var preservedStart = event.getStartsAt().plusSeconds(12_345);
        event.setStartsAt(preservedStart);
        event.setStatus(EventStatus.POSTPONED);
        market.setStatus(MarketStatus.CLOSED);
        events.saveAndFlush(event);
        markets.saveAndFlush(market);

        demoInitializer.seed();

        var reloaded = events.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getStartsAt()).isEqualTo(preservedStart);
        assertThat(reloaded.getStatus()).isEqualTo(EventStatus.POSTPONED);
        assertThat(markets.findById(market.getId()).orElseThrow().getStatus()).isEqualTo(MarketStatus.CLOSED);
    }
}
