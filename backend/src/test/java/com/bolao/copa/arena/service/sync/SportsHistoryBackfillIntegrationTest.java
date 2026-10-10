package com.bolao.copa.arena.service.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.bolao.copa.arena.config.*;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.provider.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class SportsHistoryBackfillIntegrationTest {
    private static final Instant NOW=Instant.parse("2026-10-10T12:00:00Z");
    @Autowired SportsHistoryStateStore state;
    @Autowired SportsResultHistoryStore results;
    @Autowired ConfirmedSeriesHistoryReader reader;
    @Autowired HistoricalTeamStrengthService strength;
    @Autowired SportsCatalogSyncService catalog;
    @Autowired SportsMatchSyncService operational;
    @Autowired JdbcTemplate db;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    private SportsHistoryBackfillService history;
    private SportsDataProvider provider;

    @BeforeEach void setup() {
        db.update("delete from arena_sports_history_backfill");db.update("delete from arena_sports_results_history");
        var sync=new SportsSyncProperties("PANDASCORE",true,900000,120000,300000,120000,30,7,72,20,900000);
        history=new SportsHistoryBackfillService(new SportsHistoryProperties(true,30,30000,List.of("CS2")),sync,state,results);
        provider=mock(SportsDataProvider.class);
        when(provider.providerId()).thenReturn("PANDASCORE");when(provider.supportedSports()).thenReturn(List.of("CS2"));
        when(provider.supportsHistoricalBackfill()).thenReturn(true);
    }
    private SportsMatch match(String id,Instant finish,int home,int away) {
        return new SportsMatch(id,"Official test result",new SportsTeam("701","Home",null,null),new SportsTeam("702","Away",null,null),
                new SportsChampionship("703","League",null,null,null,null,null,null,null),finish.minusSeconds(7200),finish,EventStatus.FINISHED,
                home,away,3,home>away?"701":"702",false,false,false,"CS2");
    }
    @Test void resumesPagesAndDayWindowsAfterRestartWithoutOperationalDataOrRewards() {
        long events=db.queryForObject("select count(*) from arena_events",Long.class);
        long predictions=db.queryForObject("select count(*) from arena_predictions",Long.class);
        long ledger=db.queryForObject("select count(*) from arena_point_transactions",Long.class);
        var window=history.nextWindow(provider,NOW);
        var first=match("70001",window.from().plusSeconds(600),2,0);
        when(provider.historicalPage("CS2",window.from(),window.until(),1)).thenReturn(new SportsDataProvider.HistoricalPage(List.of(first),1,true,"page1"));
        var batch=history.fetch(provider,window,NOW);history.completed(window,batch,NOW);
        assertThat(history.nextWindow(provider,NOW.plusSeconds(10))).isNull();
        var restarted=new SportsHistoryBackfillService(new SportsHistoryProperties(true,30,30000,List.of("CS2")),
                new SportsSyncProperties("PANDASCORE",true,900000,120000,300000,120000,30,7,72,20,900000),state,results);
        var second=restarted.nextWindow(provider,NOW.plusSeconds(31));
        assertThat(second.progress().nextPage()).isEqualTo(2);
        assertThat(second.from()).isEqualTo(window.from());
        when(provider.historicalPage("CS2",second.from(),second.until(),2)).thenReturn(new SportsDataProvider.HistoricalPage(List.of(first),1,false,"page2"));
        restarted.completed(second,restarted.fetch(provider,second,NOW.plusSeconds(31)),NOW.plusSeconds(31));
        assertThat(reader.read().stream().filter(r->r.id().equals("70001"))).hasSize(1);
        assertThat(state.progress("PANDASCORE").getFirst().nextPage()).isEqualTo(1);
        assertThat(state.progress("PANDASCORE").getFirst().windowEndAt()).isEqualTo(window.from());
        assertThat(db.queryForObject("select count(*) from arena_events",Long.class)).isEqualTo(events);
        assertThat(db.queryForObject("select count(*) from arena_predictions",Long.class)).isEqualTo(predictions);
        assertThat(db.queryForObject("select count(*) from arena_point_transactions",Long.class)).isEqualTo(ledger);
    }
    @Test void failedOrRepeatedPageDoesNotAdvanceTheCursor() {
        var window=history.nextWindow(provider,NOW);
        when(provider.historicalPage("CS2",window.from(),window.until(),1)).thenThrow(new SportsProviderException(SportsProviderException.Reason.RATE_LIMITED,"quota",NOW.plusSeconds(120)));
        assertThatThrownBy(()->history.fetch(provider,window,NOW)).isInstanceOf(SportsProviderException.class);
        assertThat(state.progress("PANDASCORE").getFirst().nextPage()).isEqualTo(1);
        doReturn(new SportsDataProvider.HistoricalPage(List.of(match("70002",window.from().plusSeconds(600),2,0)),1,true,"same"))
                .when(provider).historicalPage("CS2",window.from(),window.until(),1);
        history.completed(window,history.fetch(provider,window,NOW.plusSeconds(121)),NOW.plusSeconds(121));
        var next=history.nextWindow(provider,NOW.plusSeconds(160));
        when(provider.historicalPage("CS2",next.from(),next.until(),2)).thenReturn(new SportsDataProvider.HistoricalPage(List.of(),1,true,"same"));
        assertThatThrownBy(()->history.fetch(provider,next,NOW.plusSeconds(160))).hasMessageContaining("did not advance");
        assertThat(state.progress("PANDASCORE").getFirst().nextPage()).isEqualTo(2);
    }
    @Test void rejectsIncompleteScoresAndFutureDataInsteadOfInventingHistory() {
        var window=history.nextWindow(provider,NOW);
        var incomplete=match("70003",window.from().plusSeconds(600),1,0);
        var future=match("70004",window.until().plusSeconds(1),2,0);
        when(provider.historicalPage("CS2",window.from(),window.until(),1)).thenReturn(new SportsDataProvider.HistoricalPage(List.of(incomplete,future),2,false,"invalid"));
        var batch=history.fetch(provider,window,NOW);
        assertThat(batch.matches()).isEmpty();history.completed(window,batch,NOW);
        assertThat(state.progress("PANDASCORE").getFirst().rejected()).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from arena_sports_results_history",Long.class)).isZero();
    }
    @Test void noOpPreservesObservationTimeAndCorrectionCreatesNewKnowledgeTime() {
        var source=match("70005",NOW.minusSeconds(6*86400),2,0);
        results.save("PANDASCORE",List.of(source),NOW.minusSeconds(100));
        assertThat(results.save("PANDASCORE",List.of(source),NOW).unchanged()).isEqualTo(1);
        assertThat(reader.read().stream().filter(r->r.id().equals("70005")).findFirst().orElseThrow().confirmedAt()).isEqualTo(NOW.minusSeconds(100));
        results.save("PANDASCORE",List.of(match("70005",source.endedAt(),2,1)),NOW);
        assertThat(reader.read().stream().filter(r->r.id().equals("70005")).findFirst().orElseThrow().confirmedAt()).isEqualTo(NOW);
    }
    @Test void demoProviderCannotFeedOfficialHistory() {
        when(provider.demo()).thenReturn(true);
        assertThat(history.nextWindow(provider,NOW)).isNull();
        verify(provider,never()).historicalPage(anyString(),any(),any(),anyInt());
        assertThatThrownBy(()->results.save("DEMO",List.of(),NOW)).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
    @Test void transactionRollbackDoesNotLeavePartialArchiveRows() {
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            results.save("PANDASCORE",List.of(match("70006",NOW.minusSeconds(6*86400),2,0)),NOW);
            throw new IllegalStateException("rollback fixture");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(reader.read().stream().filter(r->r.id().equals("70006"))).isEmpty();
    }
    @Test void archivedOfficialResultsReachTheExistingModelWithoutInventingSparseFavorites() {
        Instant now=Instant.now();
        var evidence=new ArrayList<SportsMatch>();
        for(int i=0;i<6;i++)evidence.add(match("7100"+i,now.minusSeconds(10*86400L+i*3600L),2,0));
        results.save("PANDASCORE",evidence.subList(0,4),now);
        var home=new com.bolao.copa.arena.domain.Competitor();home.setExternalId("701");
        var away=new com.bolao.copa.arena.domain.Competitor();away.setExternalId("702");
        var sport=new com.bolao.copa.arena.domain.Sport();sport.setCode("CS2");
        var league=new com.bolao.copa.arena.domain.Championship();league.setSport(sport);
        var event=new com.bolao.copa.arena.domain.ArenaEvent();event.setChampionship(league);event.setHomeCompetitor(home);event.setAwayCompetitor(away);
        event.setExternalId("99999");event.setBestOf(3);event.setLastSyncedAt(Instant.now());
        strength.refresh();
        assertThat(strength.estimate(event).estimate().source()).isEqualTo("SYMMETRIC_PRIOR");
        assertThat(strength.estimate(event).estimate().mapProbability()).isEqualTo(.5);
        results.save("PANDASCORE",evidence.subList(4,6),Instant.now());event.setLastSyncedAt(Instant.now());strength.refresh();
        var estimated=strength.estimate(event).estimate();
        assertThat(estimated.source()).isEqualTo("PANDASCORE_CONFIRMED_RESULTS");
        assertThat(estimated.mapProbability()).isGreaterThan(.5);
        assertThat(estimated.homeSamples()).isEqualTo(6);assertThat(estimated.awaySamples()).isEqualTo(6);
    }
    @Test void operationalResultWinsWithoutDoubleCountingOrBypassingReview() {
        var source=match("72001",Instant.now().minusSeconds(6*86400L),2,0);
        results.save("PANDASCORE",List.of(source),Instant.now());
        try {
            operational.synchronize("PANDASCORE",source,catalog.synchronize("PANDASCORE",List.of(source)));
            assertThat(reader.read().stream().filter(r->r.id().equals("72001"))).hasSize(1);
            db.update("update arena_events set result_review_required=true where external_provider='PANDASCORE' and external_id='72001'");
            assertThat(reader.read().stream().filter(r->r.id().equals("72001"))).isEmpty();
        } finally {
            db.update("delete from arena_event_participants where event_id in (select id from arena_events where external_provider='PANDASCORE' and external_id='72001')");
            db.update("delete from arena_events where external_provider='PANDASCORE' and external_id='72001'");
        }
    }
}

