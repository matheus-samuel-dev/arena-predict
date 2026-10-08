package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;
import com.bolao.copa.arena.api.ArenaDtos.*;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.provider.*;
import com.bolao.copa.arena.service.sync.*;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.support.RegularTestUsers;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("test") @Transactional
class PandaScoreMarketFlowIntegrationTest {
    @Autowired SportsCatalogSyncService catalog;
    @Autowired SportsMatchSyncService sync;
    @Autowired ArenaEventRepository events;
    @Autowired PredictionMarketRepository markets;
    @Autowired MarketOptionRepository options;
    @Autowired ArenaPredictionRepository predictions;
    @Autowired ArenaPredictionService commands;
    @Autowired ArenaCatalogService api;
    @Autowired ArenaPoolRankingService rankings;
    @Autowired PointLedgerRepository ledger;
    @Autowired PointWalletService wallets;
    @Autowired UserRepository users;
    @Autowired MarketAvailabilityService availability;
    @Autowired MarketTemplateService templates;
    @Autowired DemoProbabilityEngine pricing;
    @Autowired com.bolao.copa.config.DemoProperties demoProperties;
    private final String id=UUID.randomUUID().toString().substring(0,10);
    private final Instant start=Instant.now().plusSeconds(3600);
    private SportsMatch snapshot(String sport,Integer bo,EventStatus status,Integer home,Integer away,boolean liveScore) {
        var h=new SportsTeam("home-"+id,"Equipe A",null,null); var a=new SportsTeam("away-"+id,"Equipe B",null,null);
        return new SportsMatch(id,"Série de contrato",h,a,new SportsChampionship("champ-"+id,"Competição de contrato",null,"2026",null,null,null,null,null),
                start,status==EventStatus.FINISHED?Instant.now():null,status,home,away,bo,
                status==EventStatus.FINISHED&&home!=null&&away!=null?(home>away?h.externalId():a.externalId()):null,
                false,false,liveScore,sport);
    }
    private ArenaEvent apply(SportsMatch s) {
        sync.synchronize("PANDASCORE",s,catalog.synchronize("PANDASCORE",List.of(s)));
        return events.findByExternalProviderAndExternalId("PANDASCORE",id).orElseThrow();
    }
    private PredictionMarket market(ArenaEvent e,String code){return markets.findByEventOrderByIdAsc(e).stream().filter(m->code.equals(m.getTemplateCode())).findFirst().orElseThrow();}
    @ParameterizedTest @CsvSource({"CS2,1,1","CS2,3,6","CS2,5,6","VALORANT,1,1","VALORANT,3,5","VALORANT,5,5","LEAGUE_OF_LEGENDS,1,1","LEAGUE_OF_LEGENDS,3,3","LEAGUE_OF_LEGENDS,5,3"})
    void scheduledCreatesOnlySettleableSportSpecificPrematchMarkets(String sport,int bo,int count) {
        var e=apply(snapshot(sport,bo,EventStatus.SCHEDULED,null,null,false));
        assertThat(api.eventResponse(e.getId()).availableMarketCount()).isEqualTo(count);
        assertThat(markets.findByEventOrderByIdAsc(e)).hasSize(count).allSatisfy(m->{
            assertThat(m.getTimingMode()).isEqualTo(MarketTimingMode.PRE_MATCH_ONLY);
            assertThat(m.getTemplateCode()).doesNotContain("ROUND","PISTOL","DRAGON","KILL","BARON","MAP1");
        });
        if(bo==1)return;
        if(sport.equals("LEAGUE_OF_LEGENDS")) assertThat(market(e,"TOTAL_MAPS").getName()).contains("jogos");
        assertThat(options.findByMarketOrderByIdAsc(market(e,"SERIES_SCORE"))).hasSize(bo+1)
                .allSatisfy(o->{String[] pair=o.getKey().split("_");assertThat(Math.max(Integer.parseInt(pair[0]),Integer.parseInt(pair[1]))).isEqualTo(bo/2+1);});
    }
    @ParameterizedTest @ValueSource(strings={"CS2","VALORANT","LEAGUE_OF_LEGENDS"})
    void discoveredLiveWithoutScorePublishesWinnerWithoutRetroactivePrematch(String sport) {
        var e=apply(snapshot(sport,3,EventStatus.LIVE,null,null,false));
        assertThat(markets.findByEventOrderByIdAsc(e)).singleElement().satisfies(m->{
            assertThat(m.getTemplateCode()).isEqualTo("SERIES_WINNER_LIVE");
            assertThat(m.getTimingMode()).isEqualTo(MarketTimingMode.LIVE_ONLY);
            assertThat(m.getClosesAt()).isNull();assertThat(availability.evaluate(m).allowed()).isTrue();
            assertThat(api.marketResponse(m).options()).allSatisfy(o->assertThat(o.multiplier()).isEqualByComparingTo("2.00"));
        });
        assertThat(api.eventResponse(e.getId()).homeScore()).isNull();
        assertThat(api.eventResponse(e.getId()).availableMarketCount()).isEqualTo(1);
    }
    @Test void unconfirmedFormatDoesNotPublishImpossibleContracts() {
        var e=apply(snapshot("CS2",null,EventStatus.LIVE,null,null,false));
        assertThat(markets.findByEventOrderByIdAsc(e)).isEmpty();
        assertThat(api.eventResponse(e.getId()).predictionAvailabilityLabel()).contains("indisponíveis");
    }
    @Test void scoreOpensDerivedLiveContractsAndDeactivatesImpossibleExactSelections() {
        var e=apply(snapshot("CS2",3,EventStatus.LIVE,1,0,true));
        var winner=api.marketResponse(market(e,"SERIES_WINNER_LIVE"));
        assertThat(winner.options().get(0).multiplier()).isLessThan(winner.options().get(1).multiplier());
        assertThat(options.findByMarketAndKey(market(e,"SERIES_SCORE_LIVE"),"0_2").orElseThrow().isActive()).isFalse();
        assertThat(market(e,"HOME_MAP_LIVE").getStatus()).isEqualTo(MarketStatus.CLOSED);
        assertThat(market(e,"HOME_MAP_LIVE").getStatusReason()).contains("placar ao vivo");
        assertThat(api.eventResponse(e.getId()).availableMarketCount()).isGreaterThan(1);
    }
    @Test void lossOfScoreSuspendsDerivedContractsButKeepsWinnerAvailable() {
        var e=apply(snapshot("VALORANT",3,EventStatus.LIVE,1,0,true));
        apply(snapshot("VALORANT",3,EventStatus.LIVE,null,null,false));
        assertThat(availability.evaluate(market(e,"TOTAL_MAPS_LIVE")).code()).isEqualTo("LIVE_SCORE_REQUIRED");
        assertThat(availability.evaluate(market(e,"SERIES_WINNER_LIVE")).allowed()).isTrue();
    }
    @Test void determinedTotalClosesBeforeFinalAndPrematchCannotReopen() {
        var e=apply(snapshot("CS2",3,EventStatus.SCHEDULED,null,null,false));
        e=apply(snapshot("CS2",3,EventStatus.LIVE,1,1,true));
        assertThat(market(e,"SERIES_WINNER").getStatus()).isEqualTo(MarketStatus.CLOSED);
        assertThat(availability.evaluate(market(e,"TOTAL_MAPS_LIVE")).allowed()).isFalse();
        assertThat(market(e,"TOTAL_MAPS_LIVE").getStatus()).isEqualTo(MarketStatus.CLOSED);
        templates.generate(e.getId());assertThat(market(e,"SERIES_WINNER").getStatus()).isEqualTo(MarketStatus.CLOSED);
    }
    @Test void finalResultPaysWinnerUpdatesRankingAndNeverPaysLoserOrRetries() {
        var e=apply(snapshot("CS2",3,EventStatus.LIVE,null,null,false));
        var user=RegularTestUsers.freshParticipant(users);var losingUser=RegularTestUsers.freshParticipant(users);
        var m=market(e,"SERIES_WINNER_LIVE");
        var win=commands.place(new PlacePredictionRequest(e.getId(),m.getId(),options.findByMarketAndKey(m,"HOME").orElseThrow().getId(),25,null,"win"),"win",user);
        var lose=commands.place(new PlacePredictionRequest(e.getId(),m.getId(),options.findByMarketAndKey(m,"AWAY").orElseThrow().getId(),25,null,"lose"),"lose",losingUser);
        var finalSnapshot=snapshot("CS2",3,EventStatus.FINISHED,2,1,false);
        for(int n=0;n<10;n++)apply(finalSnapshot);
        assertThat(predictions.findById(win.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.WON);
        assertThat(predictions.findById(lose.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.LOST);
        assertThat(ledger.findByIdempotencyKey("prediction-win:"+win.id())).isPresent().get().extracting(PointLedgerEntry::getAmount).isEqualTo((long)win.potentialPoints());
        assertThat(ledger.findByIdempotencyKey("prediction-win:"+lose.id())).isEmpty();
        assertThat(predictions.findById(win.id()).orElseThrow().getMultiplierModelVersion()).isEqualTo(VirtualMultiplierService.VERSION);
        assertThat(rankings.globalRanking(user).stream().filter(r->r.userId().equals(user.getId()))).singleElement().satisfies(r->{assertThat(r.correctPredictions()).isEqualTo(1);assertThat(r.totalPredictions()).isEqualTo(1);assertThat(r.points()).isEqualTo(win.potentialPoints());});
        assertThat(markets.findByEventOrderByIdAsc(e)).allMatch(x->x.getStatus()==MarketStatus.SETTLED);
        assertThat(api.eventResponse(e.getId()).availableMarketCount()).isZero();
        var finalEvent=e;
        assertThatThrownBy(()->commands.place(new PlacePredictionRequest(finalEvent.getId(),m.getId(),options.findByMarketAndKey(m,"HOME").orElseThrow().getId(),25,null,"late"),"late",user)).isInstanceOf(ArenaProblem.RuleViolation.class);
    }
    @Test void permanentlyClosedMarketRemainsClosedWhenLiveSnapshotIsStaleOrScoreDisappears() {
        var e=apply(snapshot("CS2",3,EventStatus.LIVE,1,1,true));
        var total=market(e,"TOTAL_MAPS_LIVE");
        assertThat(total.getStatus()).isEqualTo(MarketStatus.CLOSED);
        e.setLastSyncedAt(Instant.now().minusSeconds(301));e.setLiveScoreAvailable(false);
        assertThat(availability.evaluate(total).code()).isEqualTo("CLOSED");
        assertThat(availability.evaluate(total).allowed()).isFalse();
    }

    @Test void staleLiveSuspendsAndFreshIdenticalSnapshotRestoresWithoutDuplicate() {
        var snapshot=snapshot("CS2",3,EventStatus.LIVE,null,null,false);var e=apply(snapshot);
        var m=market(e,"SERIES_WINNER_LIVE");e.setLastSyncedAt(Instant.now().minusSeconds(301));
        assertThat(availability.evaluate(m).code()).isEqualTo("STALE_LIVE");
        apply(snapshot);assertThat(availability.evaluate(m).allowed()).isTrue();
        assertThat(markets.findByEventOrderByIdAsc(e)).hasSize(1);
    }
    @Test void draftIsNotPublishedByParticipantEventApi() {
        var e=apply(snapshot("CS2",1,EventStatus.SCHEDULED,null,null,false));
        var draft=new PredictionMarket();draft.setEvent(e);draft.setCode("QA_DRAFT");draft.setName("Não publicado");draft.setStatus(MarketStatus.DRAFT);markets.saveAndFlush(draft);
        assertThat(api.eventResponse(e.getId()).markets()).hasSize(1).noneMatch(m->m.code().equals("QA_DRAFT"));
    }
    @Test void simulationAndDemoAccessCannotMutateRealMatch() {
        var e=apply(snapshot("CS2",3,EventStatus.LIVE,null,null,false));
        assertThatThrownBy(()->api.recordResult(e.getId(),new EventResultRequest(2,0,true))).isInstanceOf(ArenaProblem.Conflict.class);
        var demo=users.findByEmailIgnoreCase(demoProperties.participantEmail()).orElseThrow();
        var m=market(e,"SERIES_WINNER_LIVE");
        assertThatThrownBy(()->commands.place(new PlacePredictionRequest(e.getId(),m.getId(),options.findByMarketOrderByIdAsc(m).getFirst().getId(),25,null,"demo-real"),"demo-real",demo)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(e.isDemo()).isFalse();assertThat(e.getExternalProvider()).isEqualTo("PANDASCORE");
    }
    @Test void identicalLiveSnapshotReconcilesLegacyEventWithoutMarkets() {
        var snapshot=snapshot("CS2",3,EventStatus.LIVE,null,null,false);var e=apply(snapshot);
        for(var market:markets.findByEventOrderByIdAsc(e)){options.deleteAll(options.findByMarketOrderByIdAsc(market));markets.delete(market);}
        markets.flush();apply(snapshot);
        assertThat(markets.findByEventOrderByIdAsc(e)).singleElement().extracting(PredictionMarket::getTemplateCode).isEqualTo("SERIES_WINNER_LIVE");
        assertThat(events.findByExternalProviderAndExternalId("PANDASCORE",id).orElseThrow().getId()).isEqualTo(e.getId());
    }
    @Test void alreadyFinishedImportDoesNotInventRetrospectiveMarkets() {
        var e=apply(snapshot("CS2",3,EventStatus.FINISHED,2,1,false));
        assertThat(markets.findByEventOrderByIdAsc(e)).isEmpty();
        assertThat(api.eventResponse(e.getId()).predictionAvailabilityLabel()).isEqualTo("Evento encerrado");
    }
    @ParameterizedTest @ValueSource(doubles={0,0.0001,0.01,0.125,0.25,0.5,0.75,0.99,1})
    void virtualCurveIsFiniteAndBounded(double probability) {
        assertThat(VirtualMultiplierService.multiplier(probability)).isBetween(new BigDecimal("1.10"),new BigDecimal("8.00"));
    }
    @Test void dashboardMovementAggregateCountsDebitsAndCreditsWithoutChangingBalance() {
        var user=RegularTestUsers.freshParticipant(users);
        long balance=wallets.wallet(user).balance(),before=ledger.totalAbsoluteMovement();
        wallets.apply(user,-25,PointTransactionType.PREDICTION_PLACED,"aggregate-debit-"+id,"TEST",id,"Débito de QA");
        wallets.apply(user,40,PointTransactionType.PREDICTION_WON,"aggregate-credit-"+id,"TEST",id,"Crédito de QA");
        assertThat(ledger.totalAbsoluteMovement()).isEqualTo(before+65);
        assertThat(wallets.wallet(user).balance()).isEqualTo(balance+15);
    }

    @Test void invalidNumbersAreRejectedInsteadOfPublished() {
        for(double p:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-0.1,1.1})
            assertThatThrownBy(()->VirtualMultiplierService.multiplier(p)).isInstanceOf(IllegalArgumentException.class);
    }
}
