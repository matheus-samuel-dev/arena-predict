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
