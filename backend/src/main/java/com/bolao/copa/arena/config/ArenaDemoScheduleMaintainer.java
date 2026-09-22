package com.bolao.copa.arena.config;

import com.bolao.copa.arena.domain.ArenaEvent;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.domain.ArenaEnums.MarketStatus;
import com.bolao.copa.arena.domain.ArenaEnums.MarketTimingMode;
import com.bolao.copa.arena.repository.ArenaEventRepository;
import com.bolao.copa.arena.repository.PredictionMarketRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the non-terminal demo fixtures positioned around the current clock.
 * Only their time windows move; identities, predictions, market states and
 * visitor-created records are preserved. Operator transitions also win: a
 * target is never moved after its status differs from the structural fixture.
 */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class ArenaDemoScheduleMaintainer {
    private static final List<Timeline> TIMELINES = List.of(
            new Timeline("demo-football-live", EventStatus.LIVE, -4_000, -7_200),
            new Timeline("demo-cs2-live", EventStatus.LIVE, -3_000, -5_400),
            new Timeline("demo-nba-live", EventStatus.LIVE, -1_800, -2_100),
            new Timeline("demo-tennis-live", EventStatus.LIVE, -1_800, -2_100),
            new Timeline("demo-f1-live", EventStatus.LIVE, -1_800, -2_100),
            new Timeline("demo-nba-open", EventStatus.OPEN_FOR_PREDICTIONS, 14_400, 13_500),
            new Timeline("demo-vct-open", EventStatus.OPEN_FOR_PREDICTIONS, 28_800, 27_900),
            new Timeline("demo-tennis-open", EventStatus.OPEN_FOR_PREDICTIONS, 86_400, 84_600),
            new Timeline("demo-lol-open", EventStatus.OPEN_FOR_PREDICTIONS, 172_800, 171_000),
            new Timeline("demo-f1-open", EventStatus.OPEN_FOR_PREDICTIONS, 259_200, 255_600),
            new Timeline("demo-football-open", EventStatus.SCHEDULED, 21_600, 21_300),
            new Timeline("demo-cs2-open", EventStatus.SCHEDULED, 32_400, 32_100),
            new Timeline("demo-volleyball-open", EventStatus.SCHEDULED, 36_000, 35_700),
            new Timeline("demo-american-football-open", EventStatus.SCHEDULED, 39_600, 39_300),
            new Timeline("demo-dota2-open", EventStatus.SCHEDULED, 46_800, 46_500)
    );

    private final ArenaEventRepository events;
    private final PredictionMarketRepository markets;

    public ArenaDemoScheduleMaintainer(ArenaEventRepository events, PredictionMarketRepository markets) {
        this.events = events;
        this.markets = markets;
    }

    @Scheduled(
            fixedDelayString = "${app.demo.schedule-refresh-ms:300000}",
            initialDelayString = "${app.demo.schedule-initial-delay-ms:10000}"
    )
    @Transactional
    public void refresh() {
        refresh(Instant.now());
    }

    void refresh(Instant now) {
        for (Timeline timeline : TIMELINES) {
            ArenaEvent event = events.findByExternalKeyForUpdate(timeline.externalKey()).orElse(null);
            if (event == null) continue;
            Instant previousStart = event.getStartsAt();
            boolean changed = ArenaDemoInitializer.refreshRollingDemoSchedule(
                    event,
                    timeline.expectedStatus(),
                    now.plusSeconds(timeline.startsInSeconds()),
                    now.plusSeconds(timeline.closesInSeconds()),
                    now
            );
            if (!changed) continue;

            long shiftSeconds = Duration.between(previousStart, event.getStartsAt()).getSeconds();
            markets.findByEventOrderByIdAsc(event).stream()
                    .filter(market -> market.getStatus() != MarketStatus.SETTLED
                            && market.getStatus() != MarketStatus.CANCELLED)
                    .forEach(market -> {
                        if (market.getOpensAt() != null) {
                            market.setOpensAt(market.getOpensAt().plusSeconds(shiftSeconds));
                        }
                        if (market.getClosesAt() != null) {
                            market.setClosesAt(market.getTimingMode() == MarketTimingMode.PRE_MATCH_ONLY
                                    ? event.getPredictionClosesAt()
                                    : market.getClosesAt().plusSeconds(shiftSeconds));
                        }
                    });
            events.save(event);
        }
    }

    private record Timeline(String externalKey, EventStatus expectedStatus,
                            long startsInSeconds, long closesInSeconds) {
    }
}
