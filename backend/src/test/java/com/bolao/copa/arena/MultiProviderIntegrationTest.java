package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;
import com.bolao.copa.arena.api.ArenaDtos.PlacePredictionRequest;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.provider.*;
import com.bolao.copa.arena.service.sync.*;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.support.RegularTestUsers;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("test") @Transactional
class MultiProviderIntegrationTest {
    @Autowired SportsCatalogSyncService catalog;
    @Autowired SportsMatchSyncService sync;
    @Autowired ArenaEventRepository events;
    @Autowired PredictionMarketRepository markets;
    @Autowired MarketOptionRepository options;
    @Autowired ArenaPredictionRepository predictions;
    @Autowired ArenaPredictionService command;
    @Autowired PointWalletService wallets;
    @Autowired UserRepository users;
    @Autowired ArenaCatalogService api;
    @Autowired SportsProviderRegistry registry;
    @Autowired MultiProviderSyncCoordinator coordinator;
    @Autowired EventParticipantRepository participants;
    private final Instant start=Instant.now().plusSeconds(3600);
    private SportsMatch match(String sport,EventStatus status,Integer home,Integer away) {
        var h=new SportsTeam("9101","Contract home",null,null);var a=new SportsTeam("9102","Contract away",null,null);
        return new SportsMatch("9100","Contract event",h,a,new SportsChampionship("9103","Contract championship",null,"2026",null,null,null,null,null),
                start,null,status,home,away,null,home!=null&&away!=null&&!home.equals(away)?home>away?h.externalId():a.externalId():null,
                false,home!=null&&home.equals(away),home!=null&&away!=null,sport,Map.of(),List.of(),status.name(),null,null,Set.of("score"),SportsMatch.EventFormatHint.HEAD_TO_HEAD);
    }
    private boolean apply(String provider,SportsMatch source) {return sync.synchronize(provider,source,catalog.synchronize(provider,List.of(source)));}
    @ParameterizedTest @CsvSource({"API_FOOTBALL,FOOTBALL,2,1","API_BASKETBALL,BASKETBALL,110,108","API_TENNIS,TENNIS,2,0"})
    void canonicalUpsertNoopPricingAndSettlementTravelThroughTheExistingDomain(String provider,String sport,int home,int away) {
        var scheduled=match(sport,EventStatus.SCHEDULED,null,null);
        assertThat(apply(provider,scheduled)).isTrue();assertThat(apply(provider,scheduled)).isFalse();
        var event=events.findByExternalProviderAndExternalId(provider,"9100").orElseThrow();
        assertThat(event.getChampionship().getSport().getCode()).isEqualTo(sport);assertThat(event.isDemo()).isFalse();assertThat(event.isDemoManaged()).isFalse();
        assertThat(event.getSourceSnapshotHash()).hasSize(64);assertThat(event.getBestOf()).isNull();
        var market=markets.findByEventOrderByIdAsc(event).stream().filter(m->List.of("WINNER","MATCH_WINNER").contains(m.getTemplateCode())).findFirst().orElseThrow();
        var selection=options.findByMarketAndKey(market,"HOME").orElseThrow();
        var user=RegularTestUsers.freshParticipant(users);long before=wallets.wallet(user).balance();
        var placed=command.place(new PlacePredictionRequest(event.getId(),market.getId(),selection.getId(),25,null,"multi-"+sport),"multi-"+sport,user);
        var prediction=predictions.findById(placed.id()).orElseThrow();
        assertThat(prediction.getMultiplierOrigin()).isEqualTo("INTERNAL_MODEL");assertThat(prediction.getMultiplierModelVersion()).isEqualTo("sports-prior-v3");
        var frozen=prediction.getMultiplier();
        assertThat(apply(provider,match(sport,EventStatus.LIVE,null,null))).isTrue();
        assertThat(api.liveEvents()).extracting(e->e.id()).contains(event.getId());
        assertThat(api.eventResponse(event).liveScoreAvailable()).isFalse();
        var finished=match(sport,EventStatus.FINISHED,home,away);
        assertThat(apply(provider,finished)).isTrue();long settled=wallets.wallet(user).balance();
        assertThat(settled).isEqualTo(before-25+prediction.getPotentialPoints()+300);assertThat(prediction.getStatus()).isEqualTo(PredictionStatus.WON);
        assertThat(prediction.getMultiplier()).isEqualByComparingTo(frozen);assertThat(event.getResultProcessedAt()).isNotNull();
        assertThat(apply(provider,finished)).isFalse();assertThat(wallets.wallet(user).balance()).isEqualTo(settled);
        assertThat(api.liveEvents()).extracting(e->e.id()).doesNotContain(event.getId());
        // A corrected result is quarantined, never silently reverses wallet/ranking entries.
        apply(provider,match(sport,EventStatus.FINISHED,away,home));
        assertThat(event.isResultReviewRequired()).isTrue();assertThat(wallets.wallet(user).balance()).isEqualTo(settled);
    }
    @Test void providerOwnershipSeparatesIdenticalExternalIdsAndDemoCatalog() {
        apply("API_FOOTBALL",match("FOOTBALL",EventStatus.SCHEDULED,null,null));
        apply("API_BASKETBALL",match("BASKETBALL",EventStatus.SCHEDULED,null,null));
        var football=events.findByExternalProviderAndExternalId("API_FOOTBALL","9100").orElseThrow();
        var basketball=events.findByExternalProviderAndExternalId("API_BASKETBALL","9100").orElseThrow();
        assertThat(football.getId()).isNotEqualTo(basketball.getId());assertThat(football.getHomeCompetitor().getId()).isNotEqualTo(basketball.getHomeCompetitor().getId());
        assertThat(events.findByExternalKey("demo-football-open").orElseThrow().getExternalProvider()).isNull();
    }
    @Test void postponedSuspendsAndCancelledRefundsExactlyOnce() {
        apply("API_FOOTBALL",match("FOOTBALL",EventStatus.SCHEDULED,null,null));
        var event=events.findByExternalProviderAndExternalId("API_FOOTBALL","9100").orElseThrow();
        var market=markets.findByEventOrderByIdAsc(event).getFirst();var option=options.findByMarketOrderByIdAsc(market).getFirst();
        var user=RegularTestUsers.freshParticipant(users);wallets.wallet(user);
        command.place(new PlacePredictionRequest(event.getId(),market.getId(),option.getId(),25,null,"postponed-contract"),"postponed-contract",user);
        long beforeRefund=wallets.wallet(user).balance();
        apply("API_FOOTBALL",match("FOOTBALL",EventStatus.POSTPONED,null,null));
        assertThat(market.getStatus()).isEqualTo(MarketStatus.SUSPENDED);
        var cancelled=match("FOOTBALL",EventStatus.CANCELLED,null,null);apply("API_FOOTBALL",cancelled);apply("API_FOOTBALL",cancelled);
        assertThat(wallets.wallet(user).balance()).isEqualTo(beforeRefund+25);assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
    }
    @Test void catalogPaginationAndFiltersAreAppliedInTheDatabase() {
        apply("API_FOOTBALL",match("FOOTBALL",EventStatus.SCHEDULED,null,null));
        var page=api.eventPage(EventStatus.SCHEDULED,"FOOTBALL",null,"Contract","REAL",null,start.minusSeconds(1),start.plusSeconds(1),0,1);
        assertThat(page.getTotalElements()).isEqualTo(1);assertThat(page.getContent()).hasSize(1);
        assertThat(api.eventPage(null,"BASKETBALL",null,"Contract","REAL",null,null,null,0,1)).isEmpty();
        assertThat(api.eventPage(null,null,null,"Contract","DEMO",null,null,null,0,1)).isEmpty();
        assertThatThrownBy(()->api.eventPage(null,null,null,null,null,null,null,null,0,1000)).isInstanceOf(ArenaProblem.RuleViolation.class);
    }
    @Test void optionalMissingProvidersDoNotClaimOperationalSuccess() {
        assertThat(registry.providers()).hasSize(5);
        assertThat(coordinator.providers()).filteredOn(p->!p.sync().configured()).allSatisfy(p->{
            assertThat(p.readiness()).isEqualTo("READY_FOR_CREDENTIAL");assertThat(p.sync().lastSuccessAt()).isNull();assertThat(p.sync().lastAttemptAt()).isNull();
        });assertThat(coordinator.summary().healthy()).isFalse();
    }
    @Test void raceCalendarIsNotForcedIntoTwoTeamsAndClassificationPreservesDnf() {
        var race=new SportsMatch("9200","Contract race",null,null,new SportsChampionship("9203","Contract Grand Prix",null,"2026",null,null,null,null,null),
                start,null,EventStatus.FINISHED,null,null,null,"9201",false,false,false,"MOTORSPORT",Map.of(),
                List.of(new SportsParticipant(new SportsTeam("9201","First driver",null,null),1,"1:20:00"),new SportsParticipant(new SportsTeam("9202","Second driver",null,null),null,"DNF")),
                "Completed",null,null,Set.of(),SportsMatch.EventFormatHint.RACE);
        apply("API_FORMULA1",race);
        var event=events.findByExternalProviderAndExternalId("API_FORMULA1","9200").orElseThrow();
        assertThat(event.getFormat()).isEqualTo(EventFormat.RACE);assertThat(event.getHomeCompetitor()).isNull();assertThat(event.getAwayCompetitor()).isNull();
        assertThat(participants.findByEventOrderByDisplayOrderAsc(event)).hasSize(2);assertThat(markets.findByEventOrderByIdAsc(event)).isEmpty();
        assertThat(event.getResultProcessedAt()).isNotNull();assertThat(apply("API_FORMULA1",race)).isFalse();
    }
}
