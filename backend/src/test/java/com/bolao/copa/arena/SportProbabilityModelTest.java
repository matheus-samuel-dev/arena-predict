package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.MarketDefinitionCatalog.*;
import com.bolao.copa.arena.domain.ArenaEnums.MarketTimingMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SportProbabilityModelTest {
    private static final Instant NOW=Instant.parse("2026-10-09T12:00:00Z");
    private static Definition definition(Strategy strategy,Double line,String... keys) {
        return new Definition("test","test","test",strategy,"score",line==null?null:BigDecimal.valueOf(line),MarketTimingMode.LIVE_ONLY,
                Arrays.stream(keys).map(k->new Choice(k,k,new BigDecimal("2"))).toList(),List.of(),"test");
    }
    private static double probability(Strategy strategy,Double line,String key,List<SeriesOutcomeModel.Score> scores) {
        return SeriesOutcomeModel.probability(definition(strategy,line,key),key,scores);
    }
    @ParameterizedTest @CsvSource({"1,0,0,.8,.8","3,0,0,.8,.896","5,0,0,.8,.94208","3,1,0,.5,.75","3,1,1,.5,.5","3,1,0,.4,.64","3,0,1,.4,.16"})
    void bestOfAndConditionalScoreHaveKnownProbabilities(int bo,int h,int a,double map,double expected) {
        assertThat(probability(Strategy.WINNER,null,"HOME",SeriesOutcomeModel.scores(bo,h,a,map,map))).isCloseTo(expected,within(1e-12));
    }
    @Test void totalAndWinnerDeriveFromTheSameFinalsAtEveryStage() {
        var scores=SeriesOutcomeModel.scores(3,0,0,.8,.8);
        assertThat(probability(Strategy.TOTAL,2.5,"OVER",scores)).isCloseTo(.32,within(1e-12));
        assertThat(scores.stream().filter(s->s.home()+s.away()==3).mapToDouble(SeriesOutcomeModel.Score::probability).sum()).isCloseTo(.32,within(1e-12));
        scores=SeriesOutcomeModel.scores(3,1,0,.8,.8);
        assertThat(probability(Strategy.WINNER,null,"HOME",scores)).isCloseTo(.96,within(1e-12));
        assertThat(probability(Strategy.WINNER,null,"AWAY",scores)).isCloseTo(.04,within(1e-12));
        assertThat(probability(Strategy.TOTAL,2.5,"OVER",scores)).isCloseTo(.2,within(1e-12));
        assertThat(probability(Strategy.TOTAL,2.5,"UNDER",scores)).isCloseTo(.8,within(1e-12));
    }
    @Test void normalizationSymmetryAndMonotonicityHoldAcrossTheProbabilityGrid() {
        for(int bo:List.of(1,3,5))for(int i=0;i<=100;i++) {
            double p=i/100.0;var scores=SeriesOutcomeModel.scores(bo,0,0,p,p);
            assertThat(scores.stream().mapToDouble(SeriesOutcomeModel.Score::probability).sum()).isCloseTo(1,within(1e-12));
            assertThat(scores).allSatisfy(s->{assertThat(s.probability()).isBetween(0.0,1.0);assertThat(Math.max(s.home(),s.away())).isEqualTo(bo/2+1);});
            double home=probability(Strategy.WINNER,null,"HOME",scores);
            double inverse=probability(Strategy.WINNER,null,"HOME",SeriesOutcomeModel.scores(bo,0,0,1-p,1-p));
            assertThat(home+inverse).isCloseTo(1,within(1e-12));
            if(i>0)assertThat(home).isGreaterThanOrEqualTo(probability(Strategy.WINNER,null,"HOME",SeriesOutcomeModel.scores(bo,0,0,(i-1)/100.0,(i-1)/100.0)));
        }
    }
    @Test void partialLiveLeadCanMakeAnInferiorTeamFavoriteWithoutAddingPercentages() {
        double pre=probability(Strategy.WINNER,null,"HOME",SeriesOutcomeModel.scores(3,0,0,.4,.4));
        double live=probability(Strategy.WINNER,null,"HOME",SeriesOutcomeModel.scores(3,1,0,.4,.4));
        assertThat(pre).isCloseTo(.352,within(1e-12));assertThat(live).isCloseTo(.64,within(1e-12));
    }
    @Test void drawsOnIntegerLinesAreRefundMassNotWinningProbability() {
        var scores=SeriesOutcomeModel.scores(3,0,0,.5,.5);var d=definition(Strategy.TOTAL,2.0,"OVER","UNDER");
        assertThat(SeriesOutcomeModel.probability(d,"OVER",scores)).isCloseTo(.5,within(1e-12));
        assertThat(SeriesOutcomeModel.probability(d,"UNDER",scores)).isZero();assertThat(SeriesOutcomeModel.refundProbability(d,scores)).isCloseTo(.5,within(1e-12));
        assertThat(VirtualRewardPolicy.convert(.25,.5).uncapped()).isEqualByComparingTo("2");
    }
    @Test void rewardLimitsDoNotChangeProbabilityAndInvalidNumbersAreRejected() {
        assertThat(VirtualRewardPolicy.convert(.5,0).displayed()).isEqualByComparingTo("2.00");
        var rare=VirtualRewardPolicy.convert(.01,0);assertThat(rare.uncapped()).isEqualByComparingTo("100");assertThat(rare.displayed()).isEqualByComparingTo("8");assertThat(rare.limited()).isTrue();
        for(double p:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.1,1.1}) assertThatThrownBy(()->VirtualRewardPolicy.convert(p,0)).isInstanceOf(IllegalArgumentException.class);
        for(int n=0;n<=100;n++)assertThat(VirtualRewardPolicy.convert(n/100.0,0).displayed()).isBetween(new BigDecimal("1.10"),new BigDecimal("8.00"));
    }
    private static List<BradleyTerryStrengthModel.Result> history(String home,String away,int wins,int losses,String sport) {
        var rows=new ArrayList<BradleyTerryStrengthModel.Result>();
        for(int i=0;i<wins+losses;i++)rows.add(new BradleyTerryStrengthModel.Result(home+away+i,sport,home,away,i<wins?2:0,i<wins?0:2,3,
                NOW.minusSeconds(7200),NOW.minusSeconds(3600),NOW.minusSeconds(3500),1));return rows;
    }
    @Test void sufficientVerifiedResultsCreateRelativeStrengthAndSwappingIdsIsSymmetric() {
        var model=new BradleyTerryStrengthModel(history("a","b",10,0,"CS2"),NOW,4,14);
        var forward=model.estimate("a","b",5);var reverse=model.estimate("b","a",5);
        assertThat(forward.mapProbability()).isGreaterThan(.75);assertThat(forward.mapProbability()+reverse.mapProbability()).isCloseTo(1,within(1e-12));
        assertThat(VirtualRewardPolicy.convert(forward.mapProbability(),0).displayed()).isLessThan(VirtualRewardPolicy.convert(reverse.mapProbability(),0).displayed());
        assertThat(forward.limitations()).contains("MATCH_ROSTERS_UNVERIFIED","MODEL_NOT_CALIBRATED");assertThat(forward.confidence()).isNotEqualTo("HIGH");
    }
    @Test void balancedResultsAndSparseOrDisconnectedEvidenceRemainNeutral() {
        var balanced=new BradleyTerryStrengthModel(history("a","b",5,5,"CS2"),NOW,4,14);assertThat(balanced.estimate("a","b",5).mapProbability()).isCloseTo(.5,within(1e-12));
        var sparse=new BradleyTerryStrengthModel(history("a","b",1,0,"CS2"),NOW,4,14);assertThat(sparse.estimate("a","b",5).source()).isEqualTo("SYMMETRIC_PRIOR");
        var separate=new ArrayList<>(history("a","b",10,0,"CS2"));separate.addAll(history("c","d",10,0,"CS2"));
        assertThat(new BradleyTerryStrengthModel(separate,NOW,4,14).estimate("a","c",5).limitations()).contains("DISCONNECTED_OPPONENT_GRAPH");
        assertThat(new BradleyTerryStrengthModel(List.of(),NOW,4,14).estimate("a","b",5).mapProbability()).isEqualTo(.5);
    }
    @Test void futureLabelsAndDifferentSportsCannotInfluenceTheFit() {
        assertThat(new BradleyTerryStrengthModel(history("a","b",10,0,"CS2"),NOW.minusSeconds(10000),4,14).estimate("a","b",5).mapProbability()).isEqualTo(.5);
        var mixed=new ArrayList<>(history("a","b",10,0,"CS2"));mixed.addAll(history("a","c",10,0,"VALORANT"));
        assertThatThrownBy(()->new BradleyTerryStrengthModel(mixed,NOW,4,14)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void goalsPointsAndTennisGamesUseDifferentStateDynamics() {
        var football=RealSportProbabilityService.poisson(1,0,.2,.2);var basketball=RealSportProbabilityService.basketball(110,109,47.5,.5);
        assertThat(probability(Strategy.WINNER,null,"HOME",football)).isGreaterThan(probability(Strategy.WINNER,null,"HOME",basketball));
        assertThat(football.stream().mapToDouble(SeriesOutcomeModel.Score::probability).sum()).isCloseTo(1,within(1e-12));
        assertThat(basketball.stream().mapToDouble(SeriesOutcomeModel.Score::probability).sum()).isCloseTo(1,within(1e-12));
        assertThat(RealSportProbabilityService.tennisSet(5,3,new HashMap<>())).isCloseTo(.875,within(1e-12));
        assertThat(RealSportProbabilityService.tennisSet(6,6,new HashMap<>())).isEqualTo(.5);
        assertThat(RealSportProbabilityService.tennisSet(6,4,new HashMap<>())).isEqualTo(1);
    }
}
