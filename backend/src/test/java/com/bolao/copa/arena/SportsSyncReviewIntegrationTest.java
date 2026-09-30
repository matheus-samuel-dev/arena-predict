package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;
import com.bolao.copa.arena.api.ArenaDtos.PlacePredictionRequest;
import com.bolao.copa.arena.domain.ArenaEvent;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.provider.*;
import com.bolao.copa.arena.service.sync.*;
import com.bolao.copa.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SportsSyncReviewIntegrationTest {
    private static final String PROVIDER="PANDASCORE";
    private static final SportsTeam HOME=new SportsTeam("811", "Team Liquid", "Liquid", null);
    private static final SportsTeam AWAY=new SportsTeam("812", "Natus Vincere", null, null);
    private static final SportsChampionship CHAMPIONSHIP=new SportsChampionship("813", "IEM Cologne", null,
            "2026", null, "Intel Extreme Masters", "Cologne 2026", null, null);
    @Autowired SportsCatalogSyncService catalog;
    @Autowired SportsMatchSyncService sync;
    @Autowired ArenaEventRepository events;
    @Autowired PredictionMarketRepository markets;
    @Autowired MarketOptionRepository options;
    @Autowired ArenaPredictionRepository predictions;
    @Autowired ArenaPredictionService predictionService;
    @Autowired PointWalletService wallets;
    @Autowired UserRepository users;
    @Autowired AdminAuditRepository audits;

    @ParameterizedTest
    @EnumSource(value=EventStatus.class,names={"CANCELLED","POSTPONED"})
    void officialStatusCorrectionAfterIncompleteFinishIsQuarantinedWithoutChangingPoints(EventStatus correctedStatus) {
        Instant scheduledAt=Instant.now().plusSeconds(3600);
        var event=apply(snapshot("711001",EventStatus.SCHEDULED,scheduledAt,null,null,CHAMPIONSHIP));
        var market=markets.findByEventOrderByIdAsc(event).getFirst();
        var option=options.findByMarketOrderByIdAsc(market).getFirst();
        var user=com.bolao.copa.support.RegularTestUsers.participant(users);
        var prediction=predictionService.place(new PlacePredictionRequest(event.getId(),market.getId(),option.getId(),
                25,null,"incomplete-correction"),"incomplete-correction",user);
        long balance=wallets.wallet(user).balance();
        apply(snapshot("711001",EventStatus.FINISHED,scheduledAt,Instant.now(),null,CHAMPIONSHIP));
        apply(snapshot("711001",correctedStatus,scheduledAt,null,null,CHAMPIONSHIP));
        apply(snapshot("711001",correctedStatus,scheduledAt,null,null,CHAMPIONSHIP));

        assertThat(event.getStatus()).isEqualTo(EventStatus.FINISHED);
        assertThat(event.getResultProcessedAt()).isNull();
        assertThat(event.isResultReviewRequired()).isTrue();
        assertThat(event.getPendingResultData()).contains("OFFICIAL_STATUS_CORRECTION",correctedStatus.name());
        assertThat(predictions.findById(prediction.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.ACTIVE);
        assertThat(wallets.wallet(user).balance()).isEqualTo(balance);
        assertThat(reviewAuditCount(event)).isEqualTo(1);
    }

    @Test
    void changedChampionshipPreservesPublishedIdentityAndStageAndSuspendsPredictions() {
        Instant scheduledAt=Instant.now().plusSeconds(3600);
        var event=apply(snapshot("711002",EventStatus.SCHEDULED,scheduledAt,null,null,CHAMPIONSHIP));
        Long originalChampionshipId=event.getChampionship().getId();
        String originalStage=event.getStage();
        var correctedChampionship=new SportsChampionship("814","ESL Pro League",null,"2026",null,
                "ESL Pro League",null,null,null);
        apply(snapshot("711002",EventStatus.SCHEDULED,scheduledAt,null,null,correctedChampionship));
        apply(snapshot("711002",EventStatus.SCHEDULED,scheduledAt,null,null,correctedChampionship));

        assertThat(event.getChampionship().getId()).isEqualTo(originalChampionshipId);
        assertThat(event.getStage()).isEqualTo(originalStage);
        assertThat(event.isResultReviewRequired()).isTrue();
        assertThat(event.getPendingResultData()).contains("CHAMPIONSHIP_CHANGED","\"championshipExternalId\":\"814\"","ESL Pro League");
        assertThat(markets.findByEventOrderByIdAsc(event)).allSatisfy(m -> assertThat(m.getStatus()).isEqualTo(MarketStatus.SUSPENDED));
        assertThat(reviewAuditCount(event)).isEqualTo(1);
        assertThat(tracked(Instant.now())).doesNotContain(event.getExternalId());
    }

    @Test
    void expiredIncompleteResultsAreExcludedFromTrackingAndReviewedInBoundedIdempotentBatches() {
        Instant now=Instant.now(), old=now.minus(74,ChronoUnit.HOURS), oldest=now.minus(72,ChronoUnit.HOURS);
        var withEnd=apply(snapshot("711003",EventStatus.FINISHED,old.minusSeconds(3600),old,null,CHAMPIONSHIP));
        var withoutEnd=apply(snapshot("711004",EventStatus.FINISHED,old,null,null,CHAMPIONSHIP));
        var recentFinish=apply(snapshot("711005",EventStatus.FINISHED,old,now.minusSeconds(300),null,CHAMPIONSHIP));
        var processed=apply(snapshot("711006",EventStatus.FINISHED,old,old,HOME.externalId(),CHAMPIONSHIP));
        Instant lastSynced=withEnd.getLastSyncedAt();

        assertThat(tracked(now)).contains(recentFinish.getExternalId())
                .doesNotContain(withEnd.getExternalId(),withoutEnd.getExternalId(),processed.getExternalId());
        assertThat(sync.quarantineExpiredResults(PROVIDER,oldest,1)).isEqualTo(1);
        assertThat(List.of(withEnd,withoutEnd).stream().filter(ArenaEvent::isResultReviewRequired)).hasSize(1);
        assertThat(sync.quarantineExpiredResults(PROVIDER,oldest,1)).isEqualTo(1);
        assertThat(sync.quarantineExpiredResults(PROVIDER,oldest,1)).isZero();
        for (var event:List.of(withEnd,withoutEnd)) {
            assertThat(event.isResultReviewRequired()).isTrue();
            assertThat(event.getPendingResultData()).contains("INCOMPLETE_RESULT_EXPIRED");
            assertThat(event.getResultProcessedAt()).isNull();
            assertThat(reviewAuditCount(event)).isEqualTo(1);
        }
        assertThat(withEnd.getLastSyncedAt()).isEqualTo(lastSynced);
        assertThat(recentFinish.isResultReviewRequired()).isFalse();
        assertThat(processed.isResultReviewRequired()).isFalse();
        assertThat(processed.getResultProcessedAt()).isNotNull();
    }

    private List<String> tracked(Instant now) {
        return events.findTrackedExternalIds(PROVIDER,now.plus(30,ChronoUnit.MINUTES),now.minus(72,ChronoUnit.HOURS),
                EventStatus.FINISHED,List.of(EventStatus.FINISHED,EventStatus.CANCELLED),PageRequest.of(0,100));
    }
    private long reviewAuditCount(ArenaEvent event) {
        return audits.findAll().stream().filter(a -> "EXTERNAL_RESULT_REVIEW_REQUIRED".equals(a.getAction())
                && event.getId().toString().equals(a.getResourceId())).count();
    }
    private ArenaEvent apply(SportsMatch match) {
        sync.synchronize(PROVIDER,match,catalog.synchronize(PROVIDER,List.of(match)));
        return events.findByExternalKey(PROVIDER+":"+match.externalId()).orElseThrow();
    }
    private SportsMatch snapshot(String id,EventStatus status,Instant scheduledAt,Instant endedAt,String winner,
                                 SportsChampionship championship) {
        return new SportsMatch(id,"Team Liquid vs Natus Vincere",HOME,AWAY,championship,scheduledAt,endedAt,status,
                status==EventStatus.FINISHED?2:null,status==EventStatus.FINISHED?0:null,3,winner,false,false,false);
    }
}
