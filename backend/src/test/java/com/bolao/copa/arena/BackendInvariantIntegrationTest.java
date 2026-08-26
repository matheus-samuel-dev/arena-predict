package com.bolao.copa.arena;

import static com.bolao.copa.arena.api.ArenaDtos.*;
import static org.assertj.core.api.Assertions.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class BackendInvariantIntegrationTest {
    @Autowired UserRepository users;
    @Autowired ArenaEventRepository events;
    @Autowired PredictionMarketRepository markets;
    @Autowired MarketOptionRepository options;
    @Autowired EventParticipantRepository eventParticipants;
    @Autowired AdminAuditRepository audits;
    @Autowired ArenaCatalogService catalog;
    @Autowired AdminEventResultService eventResults;
    @Autowired ArenaPredictionService predictionService;
    @Autowired PointWalletService wallets;

    @Test
    @Transactional
    void marketCannotBeReassignedAfterFirstPrediction() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var event = events.findByExternalKey("demo-tennis-open").orElseThrow();
        var otherEvent = events.findByExternalKey("demo-lol-open").orElseThrow();
        var market = markets.findByEventOrderByIdAsc(event).getFirst();
        var option = options.findByMarketOrderByIdAsc(market).getFirst();
        String key = "freeze-market-" + UUID.randomUUID();
        predictionService.place(new PlacePredictionRequest(event.getId(), market.getId(), option.getId(),
                50, null, key), key, user);

        var reassigned = marketRequest(market, otherEvent.getId(), optionRequests(market));

        assertThatThrownBy(() -> catalog.saveMarket(market.getId(), reassigned))
                .isInstanceOf(ArenaProblem.Conflict.class)
                .hasMessageContaining("estrutura");
    }

    @Test
    @Transactional
    void marketOptionsCannotChangeAfterFirstPrediction() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var event = events.findByExternalKey("demo-tennis-open").orElseThrow();
        var market = markets.findByEventOrderByIdAsc(event).getFirst();
        var option = options.findByMarketOrderByIdAsc(market).getFirst();
        String key = "freeze-options-" + UUID.randomUUID();
        predictionService.place(new PlacePredictionRequest(event.getId(), market.getId(), option.getId(),
                50, null, key), key, user);
        List<MarketOptionRequest> changed = new ArrayList<>(optionRequests(market));
        MarketOptionRequest first = changed.getFirst();
        changed.set(0, new MarketOptionRequest(first.key(), first.label(),
                first.multiplier().add(new BigDecimal("0.100")), first.active()));

        assertThatThrownBy(() -> catalog.saveMarket(market.getId(), marketRequest(market, event.getId(), changed)))
                .isInstanceOf(ArenaProblem.Conflict.class)
                .hasMessageContaining("opções");
    }

    @Test
    @Transactional
    void marketCannotOpenOnTerminalOrPostponedEvent() {
        var finished = events.findByExternalKey("demo-football-settled").orElseThrow();
        var createOnFinished = new MarketRequest(finished.getId(), "LATE_MARKET", "Mercado tardio",
                MarketStatus.OPEN, 10, List.of(
                new MarketOptionRequest("YES", "Sim", new BigDecimal("1.500"), true),
                new MarketOptionRequest("NO", "Não", new BigDecimal("1.500"), true)));
        assertThatThrownBy(() -> catalog.saveMarket(null, createOnFinished))
                .isInstanceOf(ArenaProblem.Conflict.class);

        var event = events.findByExternalKey("demo-lol-open").orElseThrow();
        var market = markets.findByEventOrderByIdAsc(event).getFirst();
        event.setStatus(EventStatus.POSTPONED);
        market.setStatus(MarketStatus.SUSPENDED);
        events.saveAndFlush(event);
        markets.saveAndFlush(market);

        assertThatThrownBy(() -> catalog.changeMarketStatus(market.getId(), MarketStatus.OPEN))
                .isInstanceOf(ArenaProblem.RuleViolation.class)
                .hasMessageContaining("aceita palpites");
    }

    @Test
    @Transactional
    void liveEventCannotReturnToPredictionPhase() {
        var event = events.findByExternalKey("demo-lol-open").orElseThrow();
        event.setStatus(EventStatus.LIVE);
        events.saveAndFlush(event);

        assertThatThrownBy(() -> catalog.saveEvent(event.getId(), eventRequest(event,
                EventStatus.OPEN_FOR_PREDICTIONS)))
                .isInstanceOf(ArenaProblem.RuleViolation.class)
                .hasMessageContaining("Transição de status do evento");
    }

    @Test
    @Transactional
    void futureEventCannotReceiveResultBeforeItStarts() {
        var event = events.findByExternalKey("demo-tennis-open").orElseThrow();

        assertThatThrownBy(() -> catalog.recordResult(event.getId(),
                new EventResultRequest(2, 0, true)))
                .isInstanceOf(ArenaProblem.RuleViolation.class)
                .hasMessageContaining("precisa começar");
    }

    @Test
    @Transactional
    void pointLedgerKeyIsIdempotentOnlyForCompatiblePayload() {
        var user = users.findByEmail("jogador@arenapredict.com").orElseThrow();
        String key = "wallet-idempotency-" + UUID.randomUUID();

        PointLedgerEntry first = wallets.apply(user, 25, PointTransactionType.ADMIN_ADJUSTMENT,
                key, "TEST", "same", "Ajuste de teste");
        PointLedgerEntry repeated = wallets.apply(user, 25, PointTransactionType.ADMIN_ADJUSTMENT,
                key, "TEST", "same", "Descrição atualizada");

        assertThat(repeated.getId()).isEqualTo(first.getId());
        assertThatThrownBy(() -> wallets.apply(user, 26, PointTransactionType.ADMIN_ADJUSTMENT,
                key, "TEST", "same", "Payload incompatível"))
                .isInstanceOf(ArenaProblem.Conflict.class)
                .hasMessageContaining("idempotência de pontos");
    }

    @Test
    @Transactional
    void raceClassificationIsCompleteAndReplaySafe() {
        var event = events.findByExternalKey("demo-f1-open").orElseThrow();
        event.setStartsAt(Instant.now().minusSeconds(60));
        events.saveAndFlush(event);
        var participants = eventParticipants.findByEventOrderByDisplayOrderAsc(event);
        var classification = new EventClassificationRequest(
                java.util.stream.IntStream.range(0, participants.size())
                        .mapToObj(index -> new EventParticipantRequest(
                                participants.get(index).getCompetitor().getId(),
                                participants.get(index).getDisplayOrder(),
                                index + 1,
                                "Marca " + (index + 1)))
                        .toList(),
                false);
        String key = "classification-" + UUID.randomUUID();
        long before = classificationAuditCount(event.getId());

        var first = eventResults.recordClassification(event.getId(), classification, key);
        var replay = eventResults.recordClassification(event.getId(), classification, key);
        eventResults.recordClassification(event.getId(), classification, key + "-same-state");

        assertThat(first.participants()).allMatch(value -> value.position() != null);
        assertThat(replay.participants()).isEqualTo(first.participants());
        assertThat(classificationAuditCount(event.getId()) - before).isEqualTo(1);

        var changed = new ArrayList<>(classification.participants());
        var firstParticipant = changed.getFirst();
        changed.set(0, new EventParticipantRequest(firstParticipant.competitorId(),
                firstParticipant.displayOrder(), firstParticipant.position(), "Outra marca"));
        assertThatThrownBy(() -> eventResults.recordClassification(event.getId(),
                new EventClassificationRequest(changed, false), key))
                .isInstanceOf(ArenaProblem.Conflict.class)
                .hasMessageContaining("dados diferentes");
    }

    private MarketRequest marketRequest(PredictionMarket market, Long eventId,
                                        List<MarketOptionRequest> requestedOptions) {
        return new MarketRequest(eventId, market.getCode(), market.getName(), market.getStatus(),
                market.getMinimumPoints(), requestedOptions);
    }

    private List<MarketOptionRequest> optionRequests(PredictionMarket market) {
        return options.findByMarketOrderByIdAsc(market).stream()
                .map(option -> new MarketOptionRequest(option.getKey(), option.getLabel(),
                        option.getMultiplier(), option.isActive()))
                .toList();
    }

    private EventRequest eventRequest(ArenaEvent event, EventStatus status) {
        return new EventRequest(event.getExternalKey(), event.getChampionship().getId(),
                event.getHomeCompetitor() == null ? null : event.getHomeCompetitor().getId(),
                event.getAwayCompetitor() == null ? null : event.getAwayCompetitor().getId(),
                event.getTitle(), event.getStage(), event.getVenue(), event.getBroadcast(), event.getImageUrl(),
                event.getStartsAt(), event.getPredictionClosesAt(), status, event.getFormat(), event.getBestOf(),
                event.isFeatured(), event.isDemo(), null);
    }

    private long classificationAuditCount(Long eventId) {
        return audits.findAll().stream()
                .filter(entry -> "EVENT_CLASSIFICATION_RECORDED".equals(entry.getAction()))
                .filter(entry -> eventId.toString().equals(entry.getResourceId()))
                .count();
    }
}
