package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;
import static com.bolao.copa.arena.api.ArenaDtos.*;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MultimarketIntegrationTest {
    @Autowired ArenaEventRepository events;
    @Autowired PredictionMarketRepository markets;
    @Autowired MarketOptionRepository options;
    @Autowired EventParticipantRepository participants;
    @Autowired ArenaPredictionRepository predictions;
    @Autowired UserRepository users;
    @Autowired ArenaCatalogService catalog;
    @Autowired MarketTemplateService templates;
    @Autowired MarketDefinitionCatalog definitions;
    @Autowired MarketAvailabilityService availability;
    @Autowired MarketSettlementEngine engine;
    @Autowired ArenaPredictionService commands;
    @Autowired AdminEventResultService results;
    @Autowired PointWalletService wallets;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ArenaPoolRankingService pools;
    @Autowired ArenaDashboardService dashboard;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;

    @ParameterizedTest
    @CsvSource({"football-open,LIVE_RESULT", "nba-open,WINNER", "tennis-open,MATCH_WINNER", "cs2-open,SERIES_WINNER", "vct-open,SERIES_WINNER", "lol-open,SERIES_WINNER"})
    void liveQuoteSnapshotSurvivesReloadAndPaysOnlyOnce(String source,String code) {
        var event=fixture(source); event.setStatus(EventStatus.LIVE);event.setHomeScore(0);event.setAwayScore(0);
        if(source.equals("football-open")) { event.setHomeScore(3);event.setAwayScore(1);event.setClock("20"); }
        if(source.equals("nba-open")) { event.setHomeScore(20);event.setAwayScore(10);event.setClock("08:00");event.setLiveData("{\"quarter\":1,\"quarterMinutes\":12}"); }
        var market=market(event,code);var user=users.findByEmail("jogador@arenapredict.com").orElseThrow();
        long before=wallets.wallet(user).balance();
        var displayed=catalog.marketResponse(market).options().stream().filter(o -> o.key().equals("HOME")).findFirst().orElseThrow();
        String intent=UUID.randomUUID().toString();
        var request=new PlacePredictionRequest(event.getId(),market.getId(),displayed.id(),40,null,intent,displayed.multiplier());
        var win=commands.place(request,intent,user);var lose=place(event,market,"AWAY",30);
        assertThat(win.multiplier()).isEqualByComparingTo(displayed.multiplier());
        assertThat(wallets.wallet(user).balance()).isEqualTo(before-70);
        Long eventId=event.getId(),marketId=market.getId();entityManager.flush();entityManager.clear();
        event=events.findById(eventId).orElseThrow();market=markets.findById(marketId).orElseThrow();
        if(source.equals("football-open")) event.setClock("83");
        else if(source.equals("nba-open")) { event.setHomeScore(110);event.setAwayScore(100);event.setClock("00:30");event.setLiveData("{\"quarter\":4,\"quarterMinutes\":12}"); }
        else event.setHomeScore(1);
        var changed=catalog.marketResponse(market).options().stream().filter(o -> o.key().equals("HOME")).findFirst().orElseThrow();
        assertThat(changed.multiplier()).isLessThan(displayed.multiplier());
        assertThat(predictions.findById(win.id()).orElseThrow().getMultiplier()).isEqualByComparingTo(displayed.multiplier());
        assertThat(commands.place(request,intent,user).id()).isEqualTo(win.id());
        var stale=new PlacePredictionRequest(eventId,marketId,displayed.id(),40,null,UUID.randomUUID().toString(),displayed.multiplier());
        assertThatThrownBy(() -> commands.place(stale,null,user)).isInstanceOf(ArenaProblem.Conflict.class).hasMessageContaining("multiplicador");
        int home=source.equals("football-open")?3:source.equals("nba-open")?115:2;
        int away=source.equals("nba-open")?101:1;
        var result=new EventResultRequest(home,away,true,data(source),true);
        results.record(eventId,result,"snapshot-"+eventId);
        entityManager.flush();entityManager.clear();
        assertThat(predictions.findById(win.id()).orElseThrow().getRewardedPoints()).isEqualTo(win.potentialPoints());
        assertThat(predictions.findById(win.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.WON);
        assertThat(predictions.findById(lose.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.LOST);
        assertThat(wallets.wallet(user).balance()).isEqualTo(before-70+win.potentialPoints());
        results.record(eventId,result,"snapshot-again-"+eventId);
        commands.settleDerived(events.findById(eventId).orElseThrow());
        assertThat(wallets.wallet(user).balance()).isEqualTo(before-70+win.potentialPoints());
    }

    @Test void suspensionCanReopenButClosureIsFinalAndAudited() {
        var event=fixture("nba-open");var market=market(event,"WINNER");
        catalog.changeMarketStatus(market.getId(),MarketStatus.SUSPENDED);
        assertThat(availability.evaluate(market).reason()).contains("administrativa registrada");
        assertThatThrownBy(() -> place(event,market,"HOME",20)).isInstanceOf(ArenaProblem.RuleViolation.class);
        catalog.changeMarketStatus(market.getId(),MarketStatus.OPEN);place(event,market,"HOME",20);
        catalog.changeMarketStatus(market.getId(),MarketStatus.CLOSED);
        assertThatThrownBy(() -> catalog.changeMarketStatus(market.getId(),MarketStatus.OPEN)).isInstanceOf(ArenaProblem.RuleViolation.class);
    }

    @Test void administrativeLiveResultPersistsClosureAndNeverReopensItOnScoreCorrection() {
        var event=fixture("football-open");event.setStatus(EventStatus.LIVE);
        var total=market(event,"TOTAL_GOALS");total.setStatus(MarketStatus.SUSPENDED);
        results.record(event.getId(),new EventResultRequest(3,1,false),"known-total-"+event.getId());
        assertThat(total.getStatus()).isEqualTo(MarketStatus.CLOSED);
        results.record(event.getId(),new EventResultRequest(0,0,false),"correct-total-"+event.getId());
        assertThat(total.getStatus()).isEqualTo(MarketStatus.CLOSED);
        assertThat(availability.evaluate(total).allowed()).isFalse();
    }

    @Test void editingLiveMarketMetadataKeepsStaticBaseAndConfirmedQuoteSeparate() {
        var event=fixture("football-open");event.setStatus(EventStatus.LIVE);event.setHomeScore(3);event.setAwayScore(1);event.setClock("83");
        var market=market(event,"LIVE_RESULT");var response=catalog.marketResponse(market);
        var home=options.findByMarketAndKey(market,"HOME").orElseThrow();var base=home.getMultiplier();
        var inputs=response.options().stream().map(o -> new MarketOptionRequest(o.key(),o.label(),o.multiplier(),o.active())).toList();
        catalog.saveMarket(market.getId(),new MarketRequest(event.getId(),market.getCode(),"Resultado ao vivo atualizado",MarketStatus.OPEN,market.getMinimumPoints(),inputs));
        assertThat(home.getMultiplier()).isEqualTo(base);
        assertThat(market.getName()).isEqualTo("Resultado ao vivo atualizado");
    }

    @Test
    void demoIncludesCancelledAndAwaitingSettlementEventsWithPersistedRules() {
        for (String key : List.of("demo-football-cancelled", "demo-football-awaiting-result")) {
            var event=events.findByExternalKey(key).orElseThrow();
            var response=catalog.eventResponse(event.getId());
            assertThat(response.availableMarketCount()).isZero();
            assertThat(response.markets()).isNotEmpty().allMatch(m -> !m.availability().allowed());
            if (response.status()==EventStatus.FINISHED)
                assertThat(response.markets()).hasSize(10).allMatch(m -> m.templateCode()!=null);
            assertThat(response.status()).isIn(EventStatus.CANCELLED,EventStatus.FINISHED);
        }
    }

    @Test
    void liveSeriesRejectsHandicapOnceAllRemainingScoresHaveTheSameOutcome() {
        var event=fixture("cs2-open");
        event.setStatus(EventStatus.LIVE); event.setHomeScore(0); event.setAwayScore(1);
        var handicap=market(event,"MAP_HANDICAP");
        assertThat(availability.evaluate(handicap).code()).isEqualTo("OUTCOME_DETERMINED");
        assertThat(availability.evaluate(market(event,"SERIES_WINNER")).allowed()).isTrue();
        assertThatThrownBy(() -> place(event,handicap,"AWAY",20)).hasMessageContaining("Palpites encerrados");
        event.setAwayScore(0);
        assertThat(availability.evaluate(handicap).allowed()).isTrue();
        event.setHomeScore(2);
        assertThat(availability.evaluate(market(event,"SERIES_WINNER")).code()).isEqualTo("OUTCOME_DETERMINED");
    }

    @Test
    void overviewUsesTheSameCancellationPermissionAsPredictionDetails() {
        var event=fixture("football-open");
        var market=market(event,"TOTAL_GOALS");
        var placed=place(event,market,"OVER",20);
        var user=users.findByEmail("jogador@arenapredict.com").orElseThrow();
        assertThat(dashboard.dashboard(user).recentPredictions().stream().filter(p -> p.id().equals(placed.id())).findFirst().orElseThrow().canCancel()).isTrue();
        market.setStatus(MarketStatus.SUSPENDED);
        assertThat(dashboard.dashboard(user).recentPredictions().stream().filter(p -> p.id().equals(placed.id())).findFirst().orElseThrow().canCancel()).isFalse();
        market.setStatus(MarketStatus.OPEN);
        event.setStatus(EventStatus.LIVE);
        assertThat(dashboard.dashboard(user).recentPredictions().stream().filter(p -> p.id().equals(placed.id())).findFirst().orElseThrow().canCancel()).isFalse();
    }

    @Test
    void eventListLoadsMarketsOptionsAndParticipantsInBoundedQueries() {
        entityManager.flush(); entityManager.clear();
        var stats=entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true); stats.clear();
        try {
            var list=catalog.listEvents(null,null,null);
            assertThat(list).hasSizeGreaterThan(10);
            assertThat(list.stream().mapToLong(e -> e.markets().size()).sum()).isGreaterThan(80);
            assertThat(stats.getPrepareStatementCount()).as("Catálogo completo sem uma consulta por evento/mercado").isLessThanOrEqualTo(6);
        } finally { stats.setStatisticsEnabled(false); }
    }

    @Test
    void leagueAutomaticallyCountsEligiblePredictionsWhileSocialPoolRequiresExplicitLink() {
        var event=fixture("football-open");
        var user=users.findByEmail("jogador@arenapredict.com").orElseThrow();
        var admin=users.findByEmail("admin@arenapredict.com").orElseThrow();
        place(event,market(event,"TOTAL_GOALS"),"OVER",20); // before membership: excluded
        var league=pools.create(new PoolRequest("Temporada teste",null,event.getChampionship().getSport().getId(),null,true,20,0,
                "Acertos elegíveis após inscrição",Instant.now().minusSeconds(60),Instant.now().plusSeconds(3600),PoolType.LEAGUE,false),admin);
        pools.joinPublic(league.id(),user);
        var social=pools.create(new PoolRequest("Amigos teste",null,null,null,true,20,0,"Palpites vinculados",null,null),user);
        var counted=place(event,market(event,"TOTAL_GOALS"),"OVER",40);
        event.setStatus(EventStatus.LIVE);
        results.record(event.getId(),new EventResultRequest(3,1,true,data("football-open"),true),"league-result-"+event.getId());
        assertThat(pools.poolRanking(league.id(),user)).singleElement().satisfies(row -> {
            assertThat(row.points()).isEqualTo(counted.potentialPoints()); assertThat(row.totalPredictions()).isEqualTo(1);
        });
        assertThat(pools.poolRanking(social.id(),user)).singleElement().satisfies(row -> assertThat(row.totalPredictions()).isZero());
    }

    @Test
    void availabilityUsesMarketWindowsAndLiveModeAtTheExactBoundary() {
        var event=fixture("football-open");
        var pre=market(event,"WINNER"); var live=market(event,"TOTAL_GOALS"); var only=market(event,"LIVE_RESULT");
        assertThat(availability.evaluate(pre,event.getPredictionClosesAt().minusNanos(1)).allowed()).isTrue();
        assertThat(availability.evaluate(pre,event.getPredictionClosesAt()).code()).isEqualTo("DEADLINE");
        assertThat(availability.evaluate(only).code()).isEqualTo("WAITING_LIVE");
        event.setStatus(EventStatus.LIVE);
        assertThat(availability.evaluate(pre).code()).isEqualTo("EVENT_STARTED");
        assertThat(availability.evaluate(live).allowed()).isTrue();
        assertThat(availability.evaluate(only).allowed()).isTrue();
        assertThat(catalog.eventResponse(event.getId()).availableMarketCount()).isPositive();
        for (var m:markets.findByEventOrderByIdAsc(event)) m.setStatus(MarketStatus.CLOSED);
        assertThat(catalog.eventResponse(event.getId()).availableMarketCount()).isZero();
        assertThat(catalog.eventResponse(event.getId()).predictionAvailabilityLabel()).doesNotContain("Aberto");
    }

    @Test
    void liveScoreClosesAlreadyDecidedTotalsAndBothScoreButKeepsResultOpen() {
        var event=fixture("football-open"); event.setStatus(EventStatus.LIVE); event.setHomeScore(2);event.setAwayScore(1);
        assertThat(availability.evaluate(market(event,"TOTAL_GOALS")).code()).isEqualTo("OUTCOME_DETERMINED");
        assertThat(availability.evaluate(market(event,"BOTH_SCORE")).allowed()).isFalse();
        assertThat(availability.evaluate(market(event,"LIVE_RESULT")).allowed()).isTrue();
        assertThatThrownBy(() -> place(event,market(event,"TOTAL_GOALS"),"OVER",40)).hasMessageContaining("já atingiu");
    }

    @ParameterizedTest
    @CsvSource({"SUSPENDED", "CLOSED", "SETTLED", "CANCELLED"})
    void closedAndSuspendedMarketsRejectPredictionsWithoutDebit(MarketStatus status) {
        var event=fixture("football-open"); var market=market(event,"TOTAL_GOALS"); market.setStatus(status);
        var user=users.findByEmail("jogador@arenapredict.com").orElseThrow();
        long before=wallets.wallet(user).balance(), count=predictions.count();
        assertThatThrownBy(() -> place(event,market,"OVER",40)).isInstanceOf(ArenaProblem.RuleViolation.class);
        assertThat(wallets.wallet(user).balance()).isEqualTo(before);
        assertThat(predictions.count()).isEqualTo(count);
    }

    @ParameterizedTest
    @CsvSource({
        "football-open,TOTAL_GOALS,OVER,UNDER,3,1",
        "nba-open,POINT_HANDICAP,HOME,AWAY,115,101",
        "tennis-open,TOTAL_GAMES,OVER,UNDER,2,1",
        "cs2-open,MAP1_TOTAL_ROUNDS,OVER,UNDER,2,1",
        "vct-open,PISTOL1,HOME,AWAY,2,1",
        "lol-open,FIRST_DRAGON,HOME,AWAY,2,1",
        "volleyball-open,TOTAL_SETS,OVER,UNDER,3,1",
        "american-football-open,HOME_TOTAL,OVER,UNDER,28,21",
        "dota2-open,FIRST_ROSHAN,HOME,AWAY,2,1"
    })
    void settlesSportSpecificMarketsAndReplaysWithoutAnotherCredit(String source,String type,String winning,String losing,int home,int away) {
        var event=fixture(source); var market=market(event,type);
        var win=place(event,market,winning,40); var lose=place(event,market,losing,30);
        assertThat(win.multiplier()).isEqualByComparingTo(options.findByMarketAndKey(market,winning).orElseThrow().getMultiplier());
        assertThat(win.potentialPoints()).isEqualTo(win.multiplier().multiply(BigDecimal.valueOf(40)).intValue());
        event.setStatus(EventStatus.LIVE);
        var request=new EventResultRequest(home,away,true,data(source),true);
        var result=results.record(event.getId(),request,"result-"+event.getId());
        assertThat(result.markets()).allMatch(m -> m.status()==MarketStatus.SETTLED);
        assertThat(predictions.findById(win.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.WON);
        assertThat(predictions.findById(lose.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.LOST);
        var user=users.findByEmail("jogador@arenapredict.com").orElseThrow();
        long after=wallets.wallet(user).balance();
        results.record(event.getId(),request,"result-"+event.getId());
        results.record(event.getId(),request,"repeat-"+event.getId());
        commands.settleDerived(event);
        assertThat(wallets.wallet(user).balance()).isEqualTo(after);
        var credits=wallets.transactions(user).stream().filter(t -> win.id().toString().equals(t.referenceId()) && t.type()==PointTransactionType.PREDICTION_WON).toList();
        assertThat(credits).hasSize(1); assertThat(credits.getFirst().amount()).isEqualTo(win.potentialPoints());
        assertThat(wallets.transactions(user)).noneMatch(t -> lose.id().toString().equals(t.referenceId()) && t.type()==PointTransactionType.PREDICTION_WON);
    }

    @Test
    void raceHasMultipleTopThreeWinnersAndStructuredClassification() {
        var event=fixture("f1-open"); var entries=participants.findByEventOrderByDisplayOrderAsc(event);
        var top=market(event,"TOP3");
        var first=place(event,top,entries.get(0).getCompetitor().getCode(),40);
        var second=place(event,top,entries.get(1).getCompetitor().getCode(),40);
        var last=place(event,top,entries.get(5).getCompetitor().getCode(),40);
        event.setStatus(EventStatus.LIVE);
        var classification=new ArrayList<EventParticipantRequest>();
        for(int i=0;i<entries.size();i++) classification.add(new EventParticipantRequest(entries.get(i).getCompetitor().getId(),i,i+1,null));
        var request=new EventClassificationRequest(classification,true,Map.of("fastestLap",entries.getFirst().getCompetitor().getCode(),"safetyCar","YES","driverClassified","YES"),true);
        results.recordClassification(event.getId(),request,"race-"+event.getId());
        assertThat(predictions.findById(first.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.WON);
        assertThat(predictions.findById(second.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.WON);
        assertThat(predictions.findById(last.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.LOST);
        var user=users.findByEmail("jogador@arenapredict.com").orElseThrow(); long balance=wallets.wallet(user).balance();
        results.recordClassification(event.getId(),request,"race-"+event.getId());
        assertThat(wallets.wallet(user).balance()).isEqualTo(balance);
        assertThat(market(event,"TOP3").getResultOptionKey().split(",")).hasSize(3);
    }

    @Test
    void cancellingEventRefundsEveryActiveMarketExactlyOnce() {
        var event=fixture("cs2-open"); var first=place(event,market(event,"PISTOL1"),"HOME",40);
        var second=place(event,market(event,"TOTAL_MAPS"),"OVER",30);
        var user=users.findByEmail("jogador@arenapredict.com").orElseThrow(); long balance=wallets.wallet(user).balance();
        assertThat(commands.cancelEvent(event.getId())).isEqualTo(2);
        assertThat(commands.cancelEvent(event.getId())).isZero();
        assertThat(wallets.wallet(user).balance()).isEqualTo(balance+70);
        assertThat(predictions.findById(first.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.REFUNDED);
        assertThat(predictions.findById(second.id()).orElseThrow().getStatus()).isEqualTo(PredictionStatus.REFUNDED);
    }

    @Test
    void generatedDefinitionsAreStableAndCannotBeManuallyOverruled() {
        var event=fixture("football-open"); var market=market(event,"TOTAL_GOALS"); String saved=market.getDefinitionData();
        int count=templates.generate(event.getId()).size();
        assertThat(templates.generate(event.getId())).hasSize(count);
        assertThat(market.getDefinitionData()).isEqualTo(saved);
        event.setStatus(EventStatus.FINISHED); market.setStatus(MarketStatus.CLOSED);
        assertThatThrownBy(() -> commands.settleMarket(market.getId(),"UNDER")).hasMessageContaining("liquidação por resultado");
    }

    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void incompleteResultRollsBackScoreMarketStatesAndAllCredits() {
        var tx=new TransactionTemplate(transactions);
        Long eventId=tx.execute(s -> { var e=fixture("football-open"); place(e,market(e,"TOTAL_GOALS"),"OVER",40); e.setStatus(EventStatus.LIVE); return e.getId(); });
        var user=users.findByEmail("jogador@arenapredict.com").orElseThrow(); long balance=wallets.wallet(user).balance();
        assertThatThrownBy(() -> results.record(eventId,new EventResultRequest(3,1,true,Map.of(),true),"incomplete-"+eventId)).hasMessageContaining("Informe");
        var persisted=catalog.eventResponse(eventId);
        assertThat(persisted.status()).isEqualTo(EventStatus.LIVE);
        assertThat(persisted.homeScore()).isNull();
        assertThat(persisted.markets()).allMatch(m -> m.status()==(m.timingMode()==MarketTimingMode.PRE_MATCH_ONLY?MarketStatus.CLOSED:MarketStatus.OPEN));
        tx.executeWithoutResult(s -> assertThat(markets.findByEventOrderByIdAsc(events.findById(eventId).orElseThrow())).allMatch(m -> m.getStatus()==MarketStatus.OPEN));
        assertThat(wallets.wallet(user).balance()).isEqualTo(balance);
        results.record(eventId,new EventResultRequest(3,1,true,data("football-open"),true),"incomplete-"+eventId);
        assertThat(catalog.eventResponse(eventId).markets()).allMatch(m -> m.status()==MarketStatus.SETTLED);
        var changed=new HashMap<>(data("football-open")); changed.put("cornersHome","8");
        assertThatThrownBy(() -> results.record(eventId,new EventResultRequest(3,1,true,changed,true),"incomplete-"+eventId))
                .isInstanceOf(ArenaProblem.Conflict.class).hasMessageContaining("dados diferentes");
    }

    @ParameterizedTest
    @CsvSource({"1,1,0","3,2,1","5,3,2"})
    void respectsSeriesLengthAndDoesNotCreateMapTotalForBo1(int bestOf,int home,int away) {
        var source=events.findByExternalKey("demo-cs2-open").orElseThrow();
        source.setBestOf(bestOf);
        var defs=definitions.definitions(source,List.of());
        assertThat(defs.stream().anyMatch(d -> d.code().equals("TOTAL_MAPS"))).isEqualTo(bestOf>1);
        source.setHomeScore(home);source.setAwayScore(away); engine.validateEvent(source,List.of(),true);
        source.setAwayScore(home);
        assertThatThrownBy(() -> engine.validateEvent(source,List.of(),true)).hasMessageContaining("Placar da série inválido");
    }

    @Test
    void basketballRejectsDrawAndImpossibleQuarterTotals() {
        var event=fixture("nba-open"); event.setHomeScore(100);event.setAwayScore(100);
        assertThatThrownBy(() -> engine.validateEvent(event,List.of(),true)).hasMessageContaining("prorrogação");
        event.setAwayScore(90); event.setResultData("{\"firstHalfHome\":\"50\",\"firstHalfAway\":\"40\",\"quarter1Home\":\"51\",\"quarter1Away\":\"20\"}");
        assertThatThrownBy(() -> engine.validateEvent(event,List.of(),true)).hasMessageContaining("primeiro quarto");
    }

    private ArenaEvent fixture(String key) {
        var source=events.findByExternalKey("demo-"+key).orElseThrow(); var event=new ArenaEvent();
        event.setExternalKey("test-multimarket-"+UUID.randomUUID());event.setTitle("Validação · "+source.getTitle());
        event.setChampionship(source.getChampionship());event.setHomeCompetitor(source.getHomeCompetitor());event.setAwayCompetitor(source.getAwayCompetitor());
        event.setFormat(source.getFormat());event.setBestOf(source.getBestOf());event.setStatus(EventStatus.SCHEDULED);
        event.setStartsAt(Instant.now().plusSeconds(3600));event.setPredictionClosesAt(Instant.now().plusSeconds(3500)); events.save(event);
        for(var p:participants.findByEventOrderByDisplayOrderAsc(source)) { var entry=new EventParticipant();entry.setEvent(event);entry.setCompetitor(p.getCompetitor());entry.setDisplayOrder(p.getDisplayOrder());participants.save(entry); }
        templates.generate(event.getId()); return event;
    }
    private PredictionMarket market(ArenaEvent event,String code) { return markets.findByEventOrderByIdAsc(event).stream().filter(m -> code.equals(m.getTemplateCode())).findFirst().orElseThrow(); }
    private PredictionResponse place(ArenaEvent event,PredictionMarket market,String key,int stake) {
        var user=users.findByEmail("jogador@arenapredict.com").orElseThrow();var option=options.findByMarketAndKey(market,key).orElseThrow();String intent=UUID.randomUUID().toString();
        return commands.place(new PlacePredictionRequest(event.getId(),market.getId(),option.getId(),stake,null,intent),intent,user);
    }
    private Map<String,String> data(String sport) {
        return switch(sport) {
            case "football-open" -> Map.of("firstHalfHome","1","firstHalfAway","0","cornersHome","7","cornersAway","4","cardsHome","3","cardsAway","2");
            case "nba-open", "american-football-open" -> Map.of("firstHalfHome","14","firstHalfAway","10","quarter1Home","7","quarter1Away","7");
            case "tennis-open" -> Map.of("set1Home","6","set1Away","4","gamesHome","17","gamesAway","14","tieBreak","NO");
            case "volleyball-open" -> Map.of("set1Home","25","set1Away","20","pointsHome","97","pointsAway","90");
            case "cs2-open" -> Map.of("map1Home","13","map1Away","10","map1Half1Home","7","map1Half1Away","5","map1Half2Home","6","map1Half2Away","5","pistol1","HOME");
            case "vct-open" -> Map.of("map1Home","13","map1Away","10","pistol1","HOME");
            case "lol-open" -> Map.of("map1Home","1","map1Away","0","firstBlood","HOME","firstTower","AWAY","firstDragon","HOME","firstBaron","NONE","killsHome","20","killsAway","10");
            case "dota2-open" -> Map.of("map1Home","1","map1Away","0","firstBlood","HOME","firstRoshan","HOME","killsHome","30","killsAway","20");
            default -> Map.of();
        };
    }
}
