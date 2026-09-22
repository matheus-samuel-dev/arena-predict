package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DemoProbabilityEngineTest {
    final MarketDefinitionCatalog definitions=new MarketDefinitionCatalog(new ObjectMapper());
    final DemoProbabilityEngine engine=new DemoProbabilityEngine(definitions,new ObjectMapper());
    final MarketAvailabilityService availability=new MarketAvailabilityService(definitions,engine);
    ArenaEvent event(String sport,int h,int a,String clock,int bestOf,String data) {
        var s=new Sport(); s.setCode(sport); var c=new Championship(); c.setSport(s);
        var e=new ArenaEvent(); e.setChampionship(c); e.setStatus(EventStatus.LIVE); e.setBestOf(bestOf);
        e.setHomeScore(h);e.setAwayScore(a);e.setClock(clock);e.setLiveData(data);
        e.setStartsAt(Instant.now().minusSeconds(300));e.setPredictionClosesAt(e.getStartsAt());return e;
    }
    PredictionMarket market(ArenaEvent e,String code) {
        var d=definitions.definitions(e,List.of()).stream().filter(v -> v.code().equals(code)).findFirst().orElseThrow();
        var m=new PredictionMarket(); m.setEvent(e); m.setTemplateCode(code); m.setStatus(MarketStatus.OPEN); m.setTimingMode(d.timingMode());definitions.snapshot(m,d);return m;
    }
    DemoProbabilityEngine.Quote quote(PredictionMarket m) {
        var selections=definitions.definition(m,List.of()).orElseThrow().options().stream().map(c -> {var o=new MarketOption();o.setKey(c.key());o.setMultiplier(c.multiplier());return o;}).toList();
        return engine.quote(m,selections);
    }
    BigDecimal home(ArenaEvent e,String code) { return quote(market(e,code)).multipliers().get("HOME"); }
    BigDecimal away(ArenaEvent e,String code) { return quote(market(e,code)).multipliers().get("AWAY"); }

    @ParameterizedTest @CsvSource(delimiter='|', value={
        "FOOTBALL|LIVE_RESULT|3|1|20|1|{}",
        "FOOTBALL|LIVE_RESULT|3|1|60|1|{}",
        "FOOTBALL|LIVE_RESULT|3|1|83:24|1|{}",
        "FOOTBALL|LIVE_RESULT|3|1|90+3|1|{}",
        "BASKETBALL|WINNER|20|10|08:00|1|{\"quarter\":1,\"quarterMinutes\":12}",
        "BASKETBALL|WINNER|80|70|08:00|1|{\"quarter\":3,\"quarterMinutes\":12}",
        "BASKETBALL|WINNER|100|90|05:00|1|{\"quarter\":4,\"quarterMinutes\":12}",
        "BASKETBALL|WINNER|110|100|00:30|1|{\"quarter\":4,\"quarterMinutes\":12}",
        "CS2|SERIES_WINNER|1|0||3|{}",
        "CS2|SERIES_WINNER|1|0||3|{\"maps\":[{\"score\":\"13-8\"},{\"score\":\"9-7\"}]}",
        "CS2|SERIES_WINNER|1|0||3|{\"currentRounds\":[12,3]}",
        "TENNIS|MATCH_WINNER|1|0||3|{\"currentGames\":[5,3]}",
        "VALORANT|SERIES_WINNER|1|0||3|{\"currentRounds\":[12,3]}"
    })
    void calibratedDemoExamples(String sport,String code,int h,int a,String clock,int bestOf,String data) {
        var e=event(sport,h,a,clock,bestOf,data);var q=quote(market(e,code));
        assertThat(q.mode()).isEqualTo("DYNAMIC");
        assertThat(q.multipliers().get("HOME")).isLessThan(q.multipliers().get("AWAY"));
        assertThat(q.multipliers().values()).allSatisfy(v -> assertThat(v).isGreaterThanOrEqualTo(DemoProbabilityEngine.MIN).isLessThan(DemoProbabilityEngine.MAX));
        assertThat(quote(market(e,code))).isEqualTo(q);
        System.out.printf("CALIBRATION %s %d:%d clock=%s BO%d %s -> %s%n",sport,h,a,clock,bestOf,data,q.multipliers());
    }

    @Test void conversionPreservesRareOutcomesInsteadOfClippingThemTogether() {
        BigDecimal previous=DemoProbabilityEngine.multiplier(0.5);
        assertThat(previous).isEqualByComparingTo("2.00");
        for(double p:new double[]{0.08,0.05,0.02,0.005,0.00001,1e-9,1e-20}) {
            BigDecimal value=DemoProbabilityEngine.multiplier(p);
            assertThat(value).isGreaterThan(previous).isLessThan(DemoProbabilityEngine.MAX);
            assertThat(DemoProbabilityEngine.multiplier(p)).isEqualTo(value);
            previous=value;
        }
    }
    @Test void strongFavoritesRemainDistinguishableUntilDisplayRounding() {
        BigDecimal previous=DemoProbabilityEngine.multiplier(0.5);
        for(double p:new double[]{0.75,0.9,0.95,0.99,0.999}) {
            BigDecimal value=DemoProbabilityEngine.multiplier(p);
            assertThat(value).isLessThan(previous).isGreaterThan(DemoProbabilityEngine.MIN);
            previous=value;
        }
        assertThat(DemoProbabilityEngine.multiplier(1)).isEqualTo(DemoProbabilityEngine.MIN);
    }
    @Test void conversionIsMonotoneFiniteAndBoundedAcrossItsDomain() {
        BigDecimal previous=DemoProbabilityEngine.MAX;
        for(int i=0;i<=10000;i++) {
            BigDecimal value=DemoProbabilityEngine.multiplier(i/10000.0);
            assertThat(value).isLessThanOrEqualTo(previous).isBetween(DemoProbabilityEngine.MIN,DemoProbabilityEngine.MAX);
            assertThat(value.scale()).isEqualTo(2);previous=value;
        }
        assertThat(DemoProbabilityEngine.multiplier(Double.MIN_VALUE)).isLessThanOrEqualTo(DemoProbabilityEngine.MAX);
        assertThat(DemoProbabilityEngine.multiplier(-1e-15)).isEqualTo(DemoProbabilityEngine.MAX);
        assertThat(DemoProbabilityEngine.multiplier(1+1e-15)).isEqualTo(DemoProbabilityEngine.MIN);
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertThatIllegalArgumentException().isThrownBy(() -> DemoProbabilityEngine.multiplier(invalid));
    }
    @Test void footballSeparatesDrawFromComebackThroughoutTheMatch() {
        var e=event("FOOTBALL",3,1,"20",1,null);
        BigDecimal leader=DemoProbabilityEngine.MAX,draw=DemoProbabilityEngine.MIN,trailing=DemoProbabilityEngine.MIN;
        for(String clock:List.of("20","60","83","90+3")) {
            e.setClock(clock);var values=quote(market(e,"LIVE_RESULT")).multipliers();
            assertThat(values.get("HOME")).isLessThan(leader);
            assertThat(values.get("DRAW")).isGreaterThan(draw).isLessThan(values.get("AWAY"));
            assertThat(values.get("AWAY")).isGreaterThan(trailing).isLessThan(DemoProbabilityEngine.MAX);
            leader=values.get("HOME");draw=values.get("DRAW");trailing=values.get("AWAY");
        }
    }
    @Test void basketballAdvantageGrowsProgressivelyAndRetainsRareComebackTail() {
        var e=event("BASKETBALL",110,100,"08:00",1,null);
        BigDecimal leader=DemoProbabilityEngine.MAX,trailing=DemoProbabilityEngine.MIN;
        int[] quarters={1,3,4,4};String[] clocks={"08:00","08:00","05:00","00:30"};
        for(int i=0;i<quarters.length;i++) {
            e.setClock(clocks[i]);e.setLiveData("{\"quarter\":"+quarters[i]+",\"quarterMinutes\":12}");
            var values=quote(market(e,"WINNER")).multipliers();
            assertThat(values.get("HOME")).isLessThan(leader);
            assertThat(values.get("AWAY")).isGreaterThan(trailing).isLessThan(DemoProbabilityEngine.MAX);
            leader=values.get("HOME");trailing=values.get("AWAY");
        }
        e.setAwayScore(102);
        assertThat(away(e,"WINNER")).isLessThan(trailing);
    }
    @ParameterizedTest @CsvSource({"CS2,3", "CS2,5", "VALORANT,3", "VALORANT,5"})
    void aSeriesLeadWithWholeMapsRemainingIsNotMatchPoint(String sport,int bestOf) {
        var e=event(sport,1,0,null,bestOf,null);
        var betweenMaps=away(e,"SERIES_WINNER");
        assertThat(betweenMaps).isLessThan(new BigDecimal("4.00"));
        e.setLiveData("{\"currentRounds\":[12,3]}");
        var mapPoint=away(e,"SERIES_WINNER");assertThat(mapPoint).isGreaterThan(betweenMaps);
        if(bestOf==5) {e.setHomeScore(2);assertThat(away(e,"SERIES_WINNER")).isGreaterThan(mapPoint);}
        assertThat(away(e,"SERIES_WINNER")).isLessThan(DemoProbabilityEngine.MAX);
    }
    @Test void tennisDistinguishesSetDisadvantageFromApproachingMatchEnd() {
        var e=event("TENNIS",1,0,null,5,null);var early=away(e,"MATCH_WINNER");
        assertThat(early).isLessThan(new BigDecimal("4.00"));
        e.setHomeScore(2);var setLead=away(e,"MATCH_WINNER");assertThat(setLead).isGreaterThan(early);
        e.setLiveData("{\"currentGames\":[4,2]}");var gamesLead=away(e,"MATCH_WINNER");assertThat(gamesLead).isGreaterThan(setLead);
        e.setLiveData("{\"currentGames\":[5,1]}");assertThat(away(e,"MATCH_WINNER")).isGreaterThan(gamesLead).isLessThan(DemoProbabilityEngine.MAX);
    }
    @Test void volleyballAndLolSeriesDoNotSaturateAtDifferentDisadvantages() {
        for(String sport:List.of("VOLLEYBALL","LEAGUE_OF_LEGENDS")) {
            var e=event(sport,1,0,null,5,null);String code=sport.equals("VOLLEYBALL")?"MATCH_WINNER":"SERIES_WINNER";
            var early=away(e,code);e.setHomeScore(2);
            assertThat(away(e,code)).isGreaterThan(early).isLessThan(DemoProbabilityEngine.MAX);
        }
    }

    @ParameterizedTest @CsvSource({"0,0,5", "1,0,30", "1,0,88", "2,0,85", "3,1,20", "3,1,83", "3,1,90+3"})
    void footballIsDeterministicBoundedAndFavorsTheLeader(int h,int a,String clock) {
        var e=event("FOOTBALL",h,a,clock,1,null);var m=market(e,"LIVE_RESULT");var q=quote(m);
        assertThat(q.mode()).isEqualTo("DYNAMIC");assertThat(quote(m)).isEqualTo(q);
        assertThat(q.multipliers().values()).allSatisfy(v -> {assertThat(v).isBetween(DemoProbabilityEngine.MIN,DemoProbabilityEngine.MAX);assertThat(v.scale()).isEqualTo(2);});
        if(h>a) assertThat(q.multipliers().get("HOME")).isLessThan(q.multipliers().get("AWAY"));
        else assertThat(q.multipliers().get("HOME")).isEqualTo(q.multipliers().get("AWAY"));
    }
    @Test void sameScoreHasVeryDifferentMeaningLateInFootball() {
        var e=event("FOOTBALL",3,1,"20",1,null);var m=market(e,"LIVE_RESULT");var early=quote(m);
        e.setClock("83");var late=quote(m);
        assertThat(late.multipliers().get("HOME")).isLessThan(early.multipliers().get("HOME"));
        assertThat(late.multipliers().get("AWAY")).isGreaterThan(early.multipliers().get("AWAY"));
        assertThat(late.multipliers().get("DRAW")).isGreaterThan(early.multipliers().get("DRAW"));
        assertThat(late.multipliers().get("HOME")).isLessThan(new BigDecimal("1.10"));
        e.setClock("90+3");assertThat(home(e,"LIVE_RESULT")).isLessThanOrEqualTo(late.multipliers().get("HOME"));
    }
    @Test void addedTimeAndMirroredTeamsAreRespected() {
        var e=event("FOOTBALL",1,0,"88",1,"{\"addedTimeMinutes\":1}");var shortTime=home(e,"LIVE_RESULT");
        e.setLiveData("{\"addedTimeMinutes\":10}");assertThat(home(e,"LIVE_RESULT")).isGreaterThan(shortTime);
        var q=quote(market(e,"LIVE_RESULT"));e.setHomeScore(0);e.setAwayScore(1);
        assertThat(quote(market(e,"LIVE_RESULT")).multipliers().get("AWAY")).isEqualTo(q.multipliers().get("HOME"));
    }
    @Test void derivedTotalsNeedActualStatisticsAndRespectTimeAndLine() {
        var e=event("FOOTBALL",3,1,"83",1,null);var corners=market(e,"TOTAL_CORNERS");
        assertThat(quote(corners).mode()).isEqualTo("STATIC");
        e.setLiveData("{\"corners\":[5,4]}");var q=quote(corners);
        assertThat(q.mode()).isEqualTo("DYNAMIC");assertThat(availability.evaluate(corners).allowed()).isTrue();
        e.setClock("90+3");assertThat(quote(corners).multipliers().get("OVER")).isGreaterThan(q.multipliers().get("OVER"));
        e.setLiveData("{\"corners\":[6,4]}");assertThat(availability.evaluate(corners).code()).isEqualTo("OUTCOME_DETERMINED");
        e.setClock("83");var goals=quote(market(e,"TOTAL_GOALS_45"));
        assertThat(goals.multipliers().get("OVER")).isGreaterThan(goals.multipliers().get("UNDER"));
        assertThat(availability.evaluate(market(e,"TOTAL_GOALS")).allowed()).isFalse();
    }
    @Test void basketballTenPointsEarlyVersusThirtySeconds() {
        var e=event("BASKETBALL",20,10,"08:00",1,"{\"quarter\":1,\"quarterMinutes\":12}");var early=home(e,"WINNER");
        e.setClock("00:30");e.setHomeScore(110);e.setAwayScore(100);e.setLiveData("{\"quarter\":4,\"quarterMinutes\":12}");
        assertThat(home(e,"WINNER")).isLessThan(early).isLessThan(new BigDecimal("1.10"));
        var totals=quote(market(e,"TOTAL_POINTS"));assertThat(totals.multipliers().get("OVER")).isGreaterThan(totals.multipliers().get("UNDER"));
    }
    @Test void tennisUsesSetsAndCurrentGamesWithoutClock() {
        var e=event("TENNIS",2,0,null,5,"{\"currentGames\":[0,0]}");var leading=home(e,"MATCH_WINNER");
        e.setAwayScore(2);var tied=home(e,"MATCH_WINNER");assertThat(leading).isLessThan(tied);
        e.setLiveData("{\"currentGames\":[5,1]}");assertThat(home(e,"MATCH_WINNER")).isLessThan(tied);
        e.setClock("90:00");assertThat(home(e,"MATCH_WINNER")).isEqualTo(home(event("TENNIS",2,2,null,5,"{\"currentGames\":[5,1]}"),"MATCH_WINNER"));
        e.setHomeScore(3);assertThat(availability.evaluate(market(e,"MATCH_WINNER")).allowed()).isFalse();
    }
    @ParameterizedTest @CsvSource({"CS2,1", "CS2,3", "CS2,5", "VALORANT,3"})
    void shootersUseRoundsAndSeriesFormat(String sport,int bestOf) {
        var e=event(sport,0,0,null,bestOf,"{\"currentRounds\":[5,3]}");var early=home(e,"SERIES_WINNER");
        e.setLiveData("{\"currentRounds\":[12,3]}");assertThat(home(e,"SERIES_WINNER")).isLessThan(early);
        if(bestOf>1) { var even=home(e,"SERIES_WINNER");e.setHomeScore(bestOf/2);assertThat(home(e,"SERIES_WINNER")).isLessThan(even); }
    }
    @Test void volleyballAndLolUseAvailableSeriesData() {
        for(String sport:List.of("VOLLEYBALL","LEAGUE_OF_LEGENDS")) {
            var e=event(sport,0,0,null,5,null);String code=sport.equals("VOLLEYBALL")?"MATCH_WINNER":"SERIES_WINNER";
            var tied=home(e,code);e.setHomeScore(2);assertThat(home(e,code)).isLessThan(tied);
        }
    }
    @Test void unsupportedDataNeverInventsDynamicPricing() {
        var e=event("BASKETBALL",10,0,null,1,null);assertThat(quote(market(e,"WINNER")).mode()).isEqualTo("STATIC");
        var m=new PredictionMarket();m.setEvent(event("MOTORSPORT",0,0,"Lap 40",1,null));m.setStatus(MarketStatus.OPEN);
        var o=new MarketOption();o.setKey("DRIVER");o.setMultiplier(new BigDecimal("3.20"));
        assertThat(engine.quote(m,List.of(o)).mode()).isEqualTo("STATIC");
    }
    @ParameterizedTest @CsvSource({"NaN", "Infinity", "-1", "83:90", "1:2:3"})
    void malformedClockFallsBackWithoutRequestFailures(String clock) {
        var e=event("FOOTBALL",1,0,clock,1,null);
        assertThat(quote(market(e,"LIVE_RESULT")).mode()).isEqualTo("STATIC");
    }
    @Test void deadlinesOverrideTemporarySuspensionAndReasonsAreTruthful() {
        var e=event("FOOTBALL",0,0,"5",1,null);var m=market(e,"LIVE_RESULT");m.setStatus(MarketStatus.SUSPENDED);
        m.setStatusReason("Aguardando confirmação de escanteio.");assertThat(availability.evaluate(m).reason()).contains("confirmação");
        m.setClosesAt(Instant.now().minusSeconds(1));assertThat(availability.evaluate(m).code()).isEqualTo("DEADLINE");
        m.setClosesAt(null);m.setStatus(MarketStatus.OPEN);assertThat(availability.evaluate(m).allowed()).isTrue();
    }
}
