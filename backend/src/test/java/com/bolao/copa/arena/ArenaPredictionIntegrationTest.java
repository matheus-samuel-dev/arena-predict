package com.bolao.copa.arena;

import static com.bolao.copa.arena.api.ArenaDtos.PlacePredictionRequest;
import static org.assertj.core.api.Assertions.*;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.domain.ArenaEnums.MarketStatus;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.repository.UserRepository;
import java.util.UUID;
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
    @Autowired PredictionMarketRepository markets;
    @Autowired MarketOptionRepository options;
    @Autowired ArenaPredictionRepository predictions;
    @Autowired ArenaPredictionService predictionService;
    @Autowired PointWalletService walletService;

    @Test
    void contextStartsWithVersionedGenericDemoData() {
        assertThat(events.findByExternalKey("demo-football-live")).isPresent();
        assertThat(events.findByExternalKey("demo-cs2-live")).isPresent();
        assertThat(markets.countByStatus(MarketStatus.OPEN)).isPositive();
    }

    @Test
    @Transactional
    void placingPredictionDebitsVirtualPointsAndIsIdempotent() {
        var user = users.findByEmail("user@bolao.com").orElseThrow();
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
        var user = users.findByEmail("user@bolao.com").orElseThrow();
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
        var user = users.findByEmail("user@bolao.com").orElseThrow();
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
}
