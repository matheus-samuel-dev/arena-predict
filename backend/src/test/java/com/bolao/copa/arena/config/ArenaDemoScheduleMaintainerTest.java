package com.bolao.copa.arena.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.bolao.copa.arena.domain.ArenaEvent;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.domain.ArenaEnums.MarketStatus;
import com.bolao.copa.arena.domain.ArenaEnums.MarketTimingMode;
import com.bolao.copa.arena.domain.PredictionMarket;
import com.bolao.copa.arena.repository.ArenaEventRepository;
import com.bolao.copa.arena.repository.PredictionMarketRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArenaDemoScheduleMaintainerTest {
    @Mock ArenaEventRepository events;
    @Mock PredictionMarketRepository markets;

    @Test
    void expiredDemoWindowMovesForwardWithoutChangingMarketState() {
        Instant now = Instant.parse("2026-09-12T12:00:00Z");
        ArenaEvent event = new ArenaEvent();
        event.setExternalKey("demo-nba-open");
        event.setDemo(true);
        event.setStatus(EventStatus.OPEN_FOR_PREDICTIONS);
        event.setStartsAt(now.minus(Duration.ofHours(1)));
        event.setPredictionClosesAt(now.minus(Duration.ofMinutes(2)));

        PredictionMarket market = new PredictionMarket();
        market.setEvent(event);
        market.setStatus(MarketStatus.SUSPENDED);
        market.setTimingMode(MarketTimingMode.PRE_MATCH_ONLY);
        market.setOpensAt(now.minus(Duration.ofHours(4)));
        market.setClosesAt(now.minus(Duration.ofMinutes(2)));

        when(events.findByExternalKeyForUpdate(anyString())).thenAnswer(invocation ->
                "demo-nba-open".equals(invocation.getArgument(0)) ? Optional.of(event) : Optional.empty());
        when(markets.findByEventOrderByIdAsc(event)).thenReturn(List.of(market));

        new ArenaDemoScheduleMaintainer(events, markets).refresh(now);

        assertThat(event.getStartsAt()).isEqualTo(now.plus(Duration.ofHours(4)));
        assertThat(event.getPredictionClosesAt()).isEqualTo(now.plus(Duration.ofMinutes(225)));
        assertThat(market.getClosesAt()).isEqualTo(event.getPredictionClosesAt());
        assertThat(market.getOpensAt()).isAfter(now);
        assertThat(market.getStatus()).isEqualTo(MarketStatus.SUSPENDED);
    }

    @Test
    void operatorStatusChangePreventsAutomaticRescheduling() {
        Instant now = Instant.parse("2026-09-12T12:00:00Z");
        ArenaEvent event = new ArenaEvent();
        event.setExternalKey("demo-nba-open");
        event.setDemo(true);
        event.setStatus(EventStatus.FINISHED);
        event.setStartsAt(now.minus(Duration.ofHours(2)));
        event.setPredictionClosesAt(now.minus(Duration.ofHours(3)));
        when(events.findByExternalKeyForUpdate(anyString())).thenAnswer(invocation ->
                "demo-nba-open".equals(invocation.getArgument(0)) ? Optional.of(event) : Optional.empty());

        new ArenaDemoScheduleMaintainer(events, markets).refresh(now);

        assertThat(event.getStartsAt()).isEqualTo(now.minus(Duration.ofHours(2)));
        assertThat(event.getStatus()).isEqualTo(EventStatus.FINISHED);
    }
}
