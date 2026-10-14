package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.service.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessResourceFailureException;

class HistoricalStrengthCacheTest {
    private final ConfirmedSeriesHistoryReader reader = mock(ConfirmedSeriesHistoryReader.class);
    private final HistoricalTeamStrengthService service = new HistoricalTeamStrengthService(reader);
    private final Instant asOf = Instant.parse("2026-10-14T12:00:00Z");

    private ArenaEvent event() {
        var sport = new Sport(); sport.setCode("CS2");
        var championship = new Championship(); championship.setSport(sport);
        var home = new Competitor(); home.setExternalId("h");
        var away = new Competitor(); away.setExternalId("a");
        var event = new ArenaEvent(); event.setChampionship(championship);
        event.setHomeCompetitor(home); event.setAwayCompetitor(away);
        event.setExternalProvider("PANDASCORE"); event.setExternalId("current");
        event.setBestOf(3); event.setLastSyncedAt(asOf);
        return event;
    }
    private List<BradleyTerryStrengthModel.Result> history(int homeScore, int awayScore, Instant observed) {
        return java.util.stream.IntStream.range(0, 6).mapToObj(i -> new BradleyTerryStrengthModel.Result(
                "past" + i, "CS2", "h", "a", homeScore, awayScore, 3,
                asOf.minusSeconds(7200), asOf.minusSeconds(3600), observed, 1)).toList();
    }
    @Test void sameConfirmedSnapshotReusesEvidenceAcrossMarketsVisitorsAndUnchangedRefreshes() {
        when(reader.read()).thenReturn(history(2, 0, asOf.minusSeconds(3000))); service.refresh();
        var first = service.estimate(event());
        for (int i = 0; i < 100; i++) assertThat(service.estimate(event())).isSameAs(first);
        service.refresh(); assertThat(service.estimate(event())).isSameAs(first);
        assertThat(first.estimate().mapProbability()).isGreaterThan(.5);
        verify(reader, times(2)).read();
    }
    @Test void changedOfficialHistoryInvalidatesEvidenceWithoutChangingSnapshotTime() {
        when(reader.read()).thenReturn(history(2, 0, asOf.minusSeconds(3000))); service.refresh();
        var first = service.estimate(event());
        when(reader.read()).thenReturn(history(0, 2, asOf.minusSeconds(3000))); service.refresh();
        var corrected = service.estimate(event());
        assertThat(corrected).isNotSameAs(first);
        assertThat(corrected.estimate().mapProbability()).isLessThan(.5);
        assertThat(corrected.revision()).isNotEqualTo(first.revision());
    }
    @Test void newSnapshotUsesOnlyEvidenceAlreadyObservedAtItsTimestamp() {
        when(reader.read()).thenReturn(history(2, 0, asOf.plusSeconds(60))); service.refresh();
        var event = event(); var first = service.estimate(event);
        assertThat(first.estimate().homeSamples()).isZero();
        event.setLastSyncedAt(asOf.plusSeconds(120));
        var next = service.estimate(event);
        assertThat(next.estimate().homeSamples()).isEqualTo(6);
        assertThat(next.estimate().mapProbability()).isGreaterThan(.5);
        assertThat(service.estimate(event)).isSameAs(next);
    }
    @Test void formatAndParticipantIdentityArePartOfTheCacheKey() {
        when(reader.read()).thenReturn(history(2, 0, asOf.minusSeconds(3000))); service.refresh();
        var event = event(); var first = service.estimate(event);
        event.setBestOf(5); var pooled = service.estimate(event);
        assertThat(pooled).isNotSameAs(first);
        assertThat(pooled.estimate().limitations()).contains("FORMAT_POOLED");
        event.getAwayCompetitor().setExternalId("unknown");
        assertThat(service.estimate(event).estimate().source()).isEqualTo("SYMMETRIC_PRIOR");
    }
    @Test void databaseFailureCannotReturnCachedHistoricalConfidence() {
        when(reader.read()).thenReturn(history(2, 0, asOf.minusSeconds(3000))); service.refresh(); service.estimate(event());
        when(reader.read()).thenThrow(new DataAccessResourceFailureException("fixture unavailable")); service.refresh();
        assertThat(service.estimate(event()).estimate().limitations()).contains("HISTORY_NOT_READY");
    }
}
