package com.bolao.copa.arena.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.domain.ArenaEvent;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ArenaDemoScheduleTest {
    private final Instant now = Instant.parse("2026-08-25T12:00:00Z");

    @Test
    void expiredOpenDemoWindowRollsForward() {
        ArenaEvent event = demoEvent(EventStatus.OPEN_FOR_PREDICTIONS,
                now.minus(Duration.ofHours(8)), now.minus(Duration.ofHours(1)));
        Instant nextStart = now.plus(Duration.ofHours(4));
        Instant nextClose = now.plus(Duration.ofHours(3));

        boolean refreshed = ArenaDemoInitializer.refreshRollingDemoSchedule(
                event, EventStatus.OPEN_FOR_PREDICTIONS, nextStart, nextClose, now);

        assertThat(refreshed).isTrue();
        assertThat(event.getStartsAt()).isEqualTo(nextStart);
        assertThat(event.getPredictionClosesAt()).isEqualTo(nextClose);
    }

    @Test
    void staleLiveDemoWindowRollsForward() {
        ArenaEvent event = demoEvent(EventStatus.LIVE,
                now.minus(Duration.ofHours(7)), now.minus(Duration.ofHours(8)));

        boolean refreshed = ArenaDemoInitializer.refreshRollingDemoSchedule(
                event, EventStatus.LIVE, now.minus(Duration.ofMinutes(30)), now.minus(Duration.ofHours(1)), now);

        assertThat(refreshed).isTrue();
        assertThat(event.getStartsAt()).isEqualTo(now.minus(Duration.ofMinutes(30)));
    }

    @Test
    void terminalOrOperationallyChangedEventIsNeverResurrected() {
        ArenaEvent event = demoEvent(EventStatus.FINISHED,
                now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(1)));
        Instant originalStart = event.getStartsAt();

        boolean refreshed = ArenaDemoInitializer.refreshRollingDemoSchedule(
                event, EventStatus.OPEN_FOR_PREDICTIONS, now.plus(Duration.ofHours(4)),
                now.plus(Duration.ofHours(3)), now);

        assertThat(refreshed).isFalse();
        assertThat(event.getStatus()).isEqualTo(EventStatus.FINISHED);
        assertThat(event.getStartsAt()).isEqualTo(originalStart);
    }

    @Test
    void currentOpenWindowKeepsItsTimelineAcrossRestarts() {
        Instant originalStart = now.plus(Duration.ofHours(4));
        Instant originalClose = now.plus(Duration.ofHours(3));
        ArenaEvent event = demoEvent(EventStatus.OPEN_FOR_PREDICTIONS, originalStart, originalClose);

        boolean refreshed = ArenaDemoInitializer.refreshRollingDemoSchedule(
                event, EventStatus.OPEN_FOR_PREDICTIONS, now.plus(Duration.ofHours(8)),
                now.plus(Duration.ofHours(7)), now);

        assertThat(refreshed).isFalse();
        assertThat(event.getStartsAt()).isEqualTo(originalStart);
        assertThat(event.getPredictionClosesAt()).isEqualTo(originalClose);
    }

    private ArenaEvent demoEvent(EventStatus status, Instant startsAt, Instant closesAt) {
        ArenaEvent event = new ArenaEvent();
        event.setDemo(true);
        event.setStatus(status);
        event.setStartsAt(startsAt);
        event.setPredictionClosesAt(closesAt);
        return event;
    }
}
