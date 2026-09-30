package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;
import com.bolao.copa.arena.api.ArenaDtos.PlacePredictionRequest;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.provider.*;
import com.bolao.copa.arena.service.sync.*;
import com.bolao.copa.repository.UserRepository;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class SportsSyncIntegrationTest {
    private static final String PROVIDER="PANDASCORE";
    private static final SportsTeam HOME=new SportsTeam("128", "Team Liquid", "Liquid", "https://cdn.example.test/liquid.png");
    private static final SportsTeam AWAY=new SportsTeam("129", "Natus Vincere", null, null);
    private static final SportsChampionship CHAMPIONSHIP=new SportsChampionship("456", "IEM Cologne", "iem-cologne", "2026",
            "https://cdn.example.test/iem.png", "Intel Extreme Masters", "Cologne 2026", null, null);
    @Autowired SportsCatalogSyncService catalog;
    @Autowired SportsMatchSyncService sync;
    @Autowired ArenaEventRepository events;
    @Autowired CompetitorRepository teams;
    @Autowired ChampionshipRepository championships;
    @Autowired PredictionMarketRepository markets;
    @Autowired MarketOptionRepository options;
    @Autowired ArenaPredictionRepository predictions;
    @Autowired ArenaPredictionService predictionService;
    @Autowired PointWalletService wallets;
    @Autowired UserRepository users;
    @Autowired ArenaCatalogService internalCatalog;

    @Test @Transactional
    void providerCatalogCannotOverwriteDemoEvenWhenNamesAndExternalIdsMatchLocalCodes() {
        var demo = events.findByExternalKey("demo-cs2-open").orElseThrow();
        var home = demo.getHomeCompetitor();
        var away = demo.getAwayCompetitor();
        var competition = demo.getChampionship();
        var status = demo.getStatus();
        var start = demo.getStartsAt();
        var source = new SportsMatch("ownership-fixture", demo.getTitle(),
                new SportsTeam(home.getCode(), home.getName(), "REAL", "https://cdn.example.test/real.png"),
                new SportsTeam(away.getCode(), away.getName(), "REAL", null),
                new SportsChampionship(competition.getSlug(), competition.getName(), competition.getSlug(), "2026",
                        null, "Official league", null, null, null),
                Instant.now().plusSeconds(3600), null, EventStatus.SCHEDULED, null, null, 3, null, false, false, false);
        apply(source);
        var official = events.findByExternalKey(PROVIDER + ":ownership-fixture").orElseThrow();
        assertThat(official.getHomeCompetitor().getId()).isNotEqualTo(home.getId());
        assertThat(official.getAwayCompetitor().getId()).isNotEqualTo(away.getId());
        assertThat(official.getChampionship().getId()).isNotEqualTo(competition.getId());
        assertThat(home.getExternalProvider()).isNull();
        assertThat(away.getExternalProvider()).isNull();
        assertThat(competition.getExternalProvider()).isNull();
        assertThat(demo.isDemo()).isTrue();
        assertThat(demo.getStatus()).isEqualTo(status);
        assertThat(demo.getStartsAt()).isEqualTo(start);
    }

    @Test @Transactional
    void manualCatalogEndpointRejectsExternalCompetitionBeforeCreatingDemoEvent() {
        apply(match("ownership-endpoint", EventStatus.SCHEDULED, null, null, null, false));
        var official = events.findByExternalKey(PROVIDER + ":ownership-endpoint").orElseThrow();
        var request = new com.bolao.copa.arena.api.ArenaDtos.EventRequest("invalid-demo-catalog",
                official.getChampionship().getId(), official.getHomeCompetitor().getId(), official.getAwayCompetitor().getId(),
                "Demo using real catalog", null, null, null, null, Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(3500), EventStatus.SCHEDULED, EventFormat.BO3, 3, false, true, null);
        assertThatThrownBy(() -> internalCatalog.saveEvent(null, request)).isInstanceOf(ArenaProblem.Conflict.class)
                .hasMessageContaining("campeonatos internos");
        assertThat(events.findByExternalKey("invalid-demo-catalog")).isEmpty();
    }

    @Test @Transactional
    void createsAndUpdatesWithoutDuplicatingMatchTeamsOrChampionship() {
        var scheduled=match("710001",EventStatus.SCHEDULED,null,null,null,false);
        apply(scheduled); apply(scheduled);
        var event=events.findByExternalKey("PANDASCORE:710001").orElseThrow();
        long id=event.getId();
        assertThat(event.isDemo()).isFalse();
        assertThat(event.getStartsAt()).isEqualTo(scheduled.scheduledAt());
        assertThat(event.getHomeCompetitor().getName()).isEqualTo("Team Liquid");
        assertThat(event.getHomeCompetitor().getImageUrl()).isEqualTo(HOME.logoUrl());
        assertThat(event.getAwayCompetitor().getAcronym()).isNull();
        assertThat(event.getChampionship().getName()).isEqualTo("IEM Cologne");
        assertThat(teams.findByExternalProviderAndExternalIdIn(PROVIDER,List.of("128","129"))).hasSize(2);
        assertThat(championships.findByExternalProviderAndExternalIdIn(PROVIDER,List.of("456"))).hasSize(1);
        apply(match("710001",EventStatus.LIVE,1,1,null,true));
        assertThat(events.findByExternalKey("PANDASCORE:710001").orElseThrow().getId()).isEqualTo(id);
        assertThat(event.getStatus()).isEqualTo(EventStatus.LIVE);
        assertThat(event.getHomeScore()).isEqualTo(1);
        assertThat(event.isLiveScoreAvailable()).isTrue();
        assertThat(event.getLastSyncedAt()).isNotNull();
    }

    @Test @Transactional
    void automaticResultSettlesExistingPredictionsExactlyOnceAndQuarantinesCorrections() {
        var scheduled=match("710002",EventStatus.SCHEDULED,null,null,null,false);
        apply(scheduled);
        var event=events.findByExternalKey("PANDASCORE:710002").orElseThrow();
        var market=markets.findByEventOrderByIdAsc(event).stream().filter(m -> "SERIES_WINNER".equals(m.getTemplateCode())).findFirst().orElseThrow();
        var option=options.findByMarketAndKey(market,"HOME").orElseThrow();
        var user=com.bolao.copa.support.RegularTestUsers.participant(users);
        var prediction=predictionService.place(new PlacePredictionRequest(event.getId(),market.getId(),option.getId(),25,null,"sync-result"),"sync-result",user);
        apply(match("710002",EventStatus.LIVE,null,null,null,false));
        assertThat(event.getHomeScore()).isNull();
        assertThat(event.isLiveScoreAvailable()).isFalse();
        var finished=match("710002",EventStatus.FINISHED,2,0,"128",false);
        apply(finished);
        assertThat(event.getStatus()).isEqualTo(EventStatus.FINISHED);
        assertThat(event.getResultProcessedAt()).isNotNull();
        assertThat(predictions.findById(prediction.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.WON);
        long balance=wallets.wallet(user).balance();
        Instant processed=event.getResultProcessedAt();
        apply(finished); apply(finished);
        assertThat(wallets.wallet(user).balance()).isEqualTo(balance);
        assertThat(event.getResultProcessedAt()).isEqualTo(processed);
        assertThat(wallets.transactions(user).stream().filter(t -> t.type()==PointTransactionType.PREDICTION_WON
                && prediction.id().toString().equals(t.referenceId()))).hasSize(1);
        apply(match("710002",EventStatus.FINISHED,2,1,"128",false));
        assertThat(event.isResultReviewRequired()).isTrue();
        assertThat(event.getAwayScore()).isZero();
        assertThat(event.getPendingResultData()).contains("OFFICIAL_RESULT_CORRECTION", "\"awayScore\":1");
        assertThat(wallets.wallet(user).balance()).isEqualTo(balance);
    }

    @Test @Transactional
    void cancelledProviderMatchRefundsOnlyOnce() {
        apply(match("710003",EventStatus.SCHEDULED,null,null,null,false));
        var event=events.findByExternalKey("PANDASCORE:710003").orElseThrow();
        var market=markets.findByEventOrderByIdAsc(event).getFirst();
        var option=options.findByMarketOrderByIdAsc(market).getFirst();
        var user=com.bolao.copa.support.RegularTestUsers.participant(users);
        var prediction=predictionService.place(new PlacePredictionRequest(event.getId(),market.getId(),option.getId(),25,null,"sync-cancel"),"sync-cancel",user);
        apply(match("710003",EventStatus.CANCELLED,null,null,null,false));
        long balance=wallets.wallet(user).balance();
        apply(match("710003",EventStatus.CANCELLED,null,null,null,false));
        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
        assertThat(predictions.findById(prediction.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.REFUNDED);
        assertThat(wallets.wallet(user).balance()).isEqualTo(balance);
    }

    @Test @Transactional
    void postponementCanResumeAndStaleUpcomingSnapshotDoesNotRegressLive() {
        apply(match("710004",EventStatus.SCHEDULED,null,null,null,false));
        apply(match("710004",EventStatus.POSTPONED,null,null,null,false));
        var event=events.findByExternalKey("PANDASCORE:710004").orElseThrow();
        assertThat(event.getStatus()).isEqualTo(EventStatus.POSTPONED);
        apply(match("710004",EventStatus.SCHEDULED,null,null,null,false));
        assertThat(event.getStatus()).isEqualTo(EventStatus.SCHEDULED);
        apply(match("710004",EventStatus.LIVE,0,1,null,true));
        apply(match("710004",EventStatus.SCHEDULED,null,null,null,false));
        assertThat(event.getStatus()).isEqualTo(EventStatus.LIVE);
        assertThat(event.getAwayScore()).isEqualTo(1);
    }

    @Test @Transactional
    void missingInformationDoesNotFabricateBoScoreScheduleOrWinner() {
        var source=match("710005",EventStatus.SCHEDULED,null,null,null,false);
        var unknownBo=new SportsMatch(source.externalId(),source.title(),HOME,AWAY,CHAMPIONSHIP,source.scheduledAt(),null,
                EventStatus.SCHEDULED,null,null,null,null,false,false,false);
        apply(unknownBo);
        var event=events.findByExternalKey("PANDASCORE:710005").orElseThrow();
        assertThat(event.getBestOf()).isNull();
        assertThat(event.getFormat()).isEqualTo(EventFormat.STANDARD);
        assertThat(markets.findByEventOrderByIdAsc(event)).isEmpty();
        apply(match("710005",EventStatus.FINISHED,2,1,null,false));
        assertThat(event.getWinnerExternalId()).isNull();
        assertThat(event.getResultProcessedAt()).isNull();
        var missing=new SportsMatch("710006",null,HOME,AWAY,null,null,null,EventStatus.SCHEDULED,null,null,null,null,false,false,false);
        apply(missing);
        assertThat(events.findByExternalKey("PANDASCORE:710006")).isEmpty();
    }

    @Test @Transactional
    void opponentOrderMayChangeWithoutChangingPredictionSides() {
        apply(match("710007",EventStatus.SCHEDULED,null,null,null,false));
        var source=match("710007",EventStatus.FINISHED,2,1,"128",false);
        apply(new SportsMatch(source.externalId(),source.title(),AWAY,HOME,CHAMPIONSHIP,source.scheduledAt(),source.endedAt(),
                source.status(),1,2,3,"128",false,false,false));
        var event=events.findByExternalKey("PANDASCORE:710007").orElseThrow();
        assertThat(event.getHomeCompetitor().getExternalId()).isEqualTo("128");
        assertThat(event.getHomeScore()).isEqualTo(2);
        assertThat(event.getResultProcessedAt()).isNotNull();
    }

    @Test
    void concurrentInsertsHaveOneDatabaseIdentity() throws Exception {
        String id="concurrent-"+UUID.randomUUID();
        var snapshot=match(id,EventStatus.SCHEDULED,null,null,null,false);
        var refs=catalog.synchronize(PROVIDER,List.of(snapshot));
        try(var executor=Executors.newFixedThreadPool(4)) {
            var start=new CountDownLatch(1);
            List<Future<Boolean>> work=new ArrayList<>();
            for(int i=0;i<4;i++) work.add(executor.submit(() -> { start.await(); return sync.synchronize(PROVIDER,snapshot,refs); }));
            start.countDown();
            for(var task:work) assertThat(task.get(30,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(events.findAll().stream().filter(e -> id.equals(e.getExternalId()))).hasSize(1);
    }

    private void apply(SportsMatch value) { sync.synchronize(PROVIDER,value,catalog.synchronize(PROVIDER,List.of(value))); }
    private SportsMatch match(String id,EventStatus status,Integer home,Integer away,String winner,boolean live) {
        return new SportsMatch(id,"Team Liquid vs Natus Vincere",HOME,AWAY,CHAMPIONSHIP,Instant.now().plusSeconds(3600),
                status==EventStatus.FINISHED?Instant.now():null,status,home,away,3,winner,false,false,live);
    }
}
