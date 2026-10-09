package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.MarketDefinitionCatalog.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class RealSportProbabilityServiceTest {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final MarketDefinitionCatalog definitions=new MarketDefinitionCatalog(json);
    private final RealSportProbabilityService service=new RealSportProbabilityService(definitions,json,new PricingSnapshotCodec(json));
    private ArenaEvent event(String code,int h,int a) {
        var sport=new Sport();sport.setCode(code);var championship=new Championship();championship.setSport(sport);championship.setName("Nome não prova a duração");
        var e=new ArenaEvent();e.setChampionship(championship);e.setStatus(EventStatus.LIVE);e.setHomeScore(h);e.setAwayScore(a);e.setExternalProvider("API_"+code);
        e.setStartsAt(Instant.now().minusSeconds(3600));e.setPredictionClosesAt(e.getStartsAt());e.setLastSyncedAt(Instant.now());e.setLiveScoreAvailable(true);return e;
    }
    private PredictionMarket market(ArenaEvent event,String... keys) {
        var market=new PredictionMarket();market.setEvent(event);market.setStatus(MarketStatus.OPEN);market.setTimingMode(MarketTimingMode.LIVE_ONLY);
        market.setTemplateCode("SOURCE_WINNER");
        definitions.snapshot(market,new Definition("SOURCE_WINNER","Vencedor","Partida",Strategy.WINNER,"score",null,MarketTimingMode.LIVE_ONLY,
                Arrays.stream(keys).map(k->new Choice(k,k,new BigDecimal("2.00"))).toList(),List.of(),"test"));return market;
    }
    private DemoProbabilityEngine.Quote quote(PredictionMarket m) {
        var options=definitions.definition(m,List.of()).orElseThrow().options().stream().map(c->{var o=new MarketOption();o.setKey(c.key());o.setMultiplier(c.multiplier());return o;}).toList();return service.quote(m,options);
    }
    @Test void realFootballConditionsOnCanonicalScoreAndElapsedRegulationTime() {
        var e=event("FOOTBALL",1,0);e.setSourceStatus("2H");e.setClock("75");var q=quote(market(e,"HOME","DRAW","AWAY"));
        assertThat(q.assessment().available()).isTrue();assertThat(q.assessment().probabilities().get("HOME")).isGreaterThan(.8);
        assertThat(q.assessment().probabilities().values().stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(1,within(1e-12));
        assertThat(q.assessment().confidence()).isEqualTo("LOW");assertThat(q.assessment().limitations()).contains("ADDED_TIME_NOT_ESTIMATED");
    }
    @Test void unknownFootballScopeStaleClockOrExtraTimeCannotBePricedAsNinetyMinutes() {
        var e=event("FOOTBALL",3,1);e.setSourceStatus("ET");e.setClock("110");assertThat(quote(market(e,"HOME","DRAW","AWAY")).assessment().available()).isFalse();
        e.setSourceStatus("2H");e.setClock("90");assertThat(quote(market(e,"HOME","DRAW","AWAY")).assessment().available()).isFalse();
        e.setClock("60");e.setLastSyncedAt(Instant.now().minusSeconds(301));assertThat(quote(market(e,"HOME","DRAW","AWAY")).assessment().confidence()).isEqualTo("NONE");
    }
    @Test void basketballUsesPeriodDurationAndCurrentScoringRatherThanGoalDynamicsOrDemoData() {
        var e=event("BASKETBALL",110,100);e.setSourceStatus("Q4");e.setClock("00:30");e.setLiveData("{\"quarterMinutes\":12}");
        assertThat(quote(market(e,"HOME","AWAY")).assessment().available()).isFalse();
        e.setResultData("{\"quarterMinutes\":\"12\"}");var q=quote(market(e,"HOME","AWAY"));
        assertThat(q.assessment().available()).isTrue();assertThat(q.assessment().probabilities().get("HOME")).isGreaterThan(.99);
        assertThat(q.assessment().limitations()).contains("SCORING_PRIOR_18_POINTS_OVER_8_MINUTES");
        assertThat(q.assessment().probabilities().get("AWAY")).isGreaterThan(0);
    }
    @Test void tennisUsesGamesWithinTheCurrentSetAndDisclosesMissingServeState() {
        var e=event("TENNIS",1,0);e.setBestOf(3);e.setResultData("{\"set2Home\":\"5\",\"set2Away\":\"3\"}");
        var q=quote(market(e,"HOME","AWAY"));assertThat(q.assessment().probabilities().get("HOME")).isCloseTo(.9375,within(1e-12));
        assertThat(q.assessment().limitations()).contains("SERVE_AND_POINT_STATE_UNAVAILABLE");
        e.setBestOf(null);assertThat(quote(market(e,"HOME","AWAY")).assessment().available()).isFalse();
    }
}
