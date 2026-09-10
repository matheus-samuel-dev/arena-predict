package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.MarketDefinitionCatalog.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Independent result examples: these assertions do not repeat the implementation formula. */
class MarketSettlementRulesTest {
    private final ObjectMapper json=new ObjectMapper();
    private final MarketDefinitionCatalog catalog=new MarketDefinitionCatalog(json);
    private final MarketSettlementEngine engine=new MarketSettlementEngine(json);

    @ParameterizedTest
    @CsvSource({
        "CS2,13,0", "CS2,13,11", "CS2,16,12", "CS2,16,13", "CS2,14,16", "CS2,19,17", "CS2,22,18",
        "VALORANT,13,0", "VALORANT,13,11", "VALORANT,14,12", "VALORANT,13,15", "VALORANT,20,18"
    })
    void acceptsCompletedRegulationAndSportSpecificOvertimes(String sport,int home,int away) {
        var event=event(sport,2,1); data(event,Map.of("map1Home",""+home,"map1Away",""+away));
        assertThatCode(() -> engine.validateEvent(event,List.of(),true)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
        "CS2,12,10", "CS2,13,12", "CS2,14,12", "CS2,15,13", "CS2,16,11", "CS2,17,15", "CS2,19,14",
        "VALORANT,13,12", "VALORANT,14,11", "VALORANT,16,13", "VALORANT,15,15"
    })
    void rejectsImpossibleCompletedShooterMaps(String sport,int home,int away) {
        var event=event(sport,2,1); data(event,Map.of("map1Home",""+home,"map1Away",""+away));
        assertThatThrownBy(() -> engine.validateEvent(event,List.of(),true)).hasMessageContaining("primeiro mapa");
    }

    @Test
    void halvesExcludeOvertimeAndPistolMustBelongToAnActualRoundWinner() {
        var event=event("CS2",2,1);
        data(event,Map.of("map1Home","16","map1Away","14","map1Half1Home","7","map1Half1Away","5","map1Half2Home","5","map1Half2Away","7"));
        engine.validateEvent(event,List.of(),true);
        assertWinner(event,"MAP1_HALF1","HOME"); assertWinner(event,"MAP1_HALF2","AWAY"); assertWinner(event,"MAP1_TOTAL_ROUNDS","OVER");
        data(event,Map.of("map1Home","16","map1Away","14","map1Half1Home","7","map1Half1Away","5","map1Half2Home","9","map1Half2Away","9"));
        assertThatThrownBy(() -> engine.validateEvent(event,List.of(),true)).hasMessageContaining("regulamentar");
        data(event,Map.of("map1Home","13","map1Away","0","pistol1","AWAY"));
        assertThatThrownBy(() -> engine.validateEvent(event,List.of(),true)).hasMessageContaining("pistol");
    }

    @ParameterizedTest
    @CsvSource({"25,0,true","25,23,true","26,24,true","31,33,true","25,24,false","26,20,false","27,24,false","24,22,false"})
    void volleyballSetEndsAsSoonAsTheWinningThresholdIsReached(int home,int away,boolean valid) {
        var event=event("VOLLEYBALL",3,1); data(event,Map.of("set1Home",""+home,"set1Away",""+away));
        if (valid) assertThatCode(() -> engine.validateEvent(event,List.of(),true)).doesNotThrowAnyException();
        else assertThatThrownBy(() -> engine.validateEvent(event,List.of(),true)).hasMessageContaining("primeiro set");
    }

    @ParameterizedTest
    @CsvSource({
        "2,1,6,4,17,14,NO,true", // 6-4, 5-7, 6-3
        "2,0,7,6,13,10,YES,true", // 7-6, 6-4
        "2,0,6,0,12,0,NO,true", // two bagels
        "2,0,7,6,13,10,NO,false",
        "2,0,6,0,12,0,YES,false",
        "2,0,6,4,20,16,NO,false",
        "2,1,6,4,12,8,NO,false",
        "2,0,6,4,13,8,NO,false",
        "1,2,4,6,14,17,NO,true"
    })
    void tennisGamesMustBeAchievableByTheReportedSetsAndTieBreak(int home,int away,int firstHome,int firstAway,int gamesHome,int gamesAway,String tieBreak,boolean valid) {
        var event=event("TENNIS",home,away);
        data(event,Map.of("set1Home",""+firstHome,"set1Away",""+firstAway,"gamesHome",""+gamesHome,"gamesAway",""+gamesAway,"tieBreak",tieBreak));
        if (valid) assertThatCode(() -> engine.validateEvent(event,List.of(),true)).doesNotThrowAnyException();
        else assertThatThrownBy(() -> engine.validateEvent(event,List.of(),true)).isInstanceOf(ArenaProblem.RuleViolation.class);
    }

    @Test
    void tennisBestOfFiveAcceptsACompletedFiveSetMatch() {
        var event=event("TENNIS",3,2); event.setBestOf(5);
        // 6-4, 4-6, 7-6, 0-6, 6-3.
        data(event,Map.of("set1Home","6","set1Away","4","gamesHome","23","gamesAway","25","tieBreak","YES"));
        engine.validateEvent(event,List.of(),true);
        assertWinner(event,"MATCH_WINNER","HOME"); assertWinner(event,"GAME_HANDICAP","AWAY");
        assertWinner(event,"TOTAL_SETS","OVER"); assertWinner(event,"SET_SCORE","3_2");
    }

    @Test
    void footballSettlesMultipleWinningSelectionsAndAllScoreCategories() {
        var event=event("FOOTBALL",3,1);
        data(event,Map.of("firstHalfHome","0","firstHalfAway","1","cornersHome","7","cornersAway","3","cardsHome","1","cardsAway","2"));
        assertWinner(event,"WINNER","HOME"); assertWinner(event,"LIVE_RESULT","HOME");
        assertWinner(event,"TOTAL_GOALS","OVER"); assertWinner(event,"BOTH_SCORE","YES");
        assertWinner(event,"CORRECT_SCORE","3_1"); assertWinner(event,"FIRST_HALF_WINNER","AWAY");
        assertWinner(event,"TOTAL_CORNERS","OVER"); assertWinner(event,"TOTAL_CARDS","UNDER");
        assertThat(engine.evaluate(definition(event,"DOUBLE_CHANCE"),event,List.of()).winningKeys()).containsExactlyInAnyOrder("HOME_DRAW","HOME_AWAY");
        event.setHomeScore(0); event.setAwayScore(0);
        assertWinner(event,"WINNER","DRAW"); assertWinner(event,"BOTH_SCORE","NO");
        assertThat(engine.evaluate(definition(event,"DOUBLE_CHANCE"),event,List.of()).winningKeys()).containsExactlyInAnyOrder("HOME_DRAW","DRAW_AWAY");
        event.setHomeScore(5); event.setAwayScore(1); assertWinner(event,"CORRECT_SCORE","OTHER");
    }

    @Test
    void basketballUsesOwnTeamTotalAndMarginBoundaries() {
        var event=event("BASKETBALL",108,118);
        assertWinner(event,"HOME_TOTAL","UNDER"); assertWinner(event,"TOTAL_POINTS","OVER");
        assertWinner(event,"POINT_HANDICAP","AWAY"); assertWinner(event,"WIN_MARGIN","AWAY_SMALL");
        event.setAwayScore(119); assertWinner(event,"WIN_MARGIN","AWAY_LARGE");
        var american=event("AMERICAN_FOOTBALL",21,21);
        assertWinner(american,"WINNER","DRAW"); assertWinner(american,"WIN_MARGIN","DRAW");
    }

    @Test
    void allTotalsAndHandicapsRefundExactlyOnAnIntegerLine() {
        var event=event("BASKETBALL",110,100);
        for (var entry:Map.of("TOTAL_POINTS","210","HOME_TOTAL","110","POINT_HANDICAP","-10").entrySet()) {
            var original=definition(event,entry.getKey());
            var integerLine=new Definition(original.code(),original.name(),original.category(),original.strategy(),original.metric(),new BigDecimal(entry.getValue()),original.timingMode(),original.options(),original.fields(),original.settlementDescription());
            var outcome=engine.evaluate(integerLine,event,List.of());
            assertThat(outcome.refund()).as(entry.getKey()).isTrue(); assertThat(outcome.winningKeys()).isEmpty();
        }
        event.setAwayScore(110);
        assertThat(engine.evaluate(definition(event,"WINNER"),event,List.of()).refund()).isTrue();
    }

    @Test
    void esportsSeriesAndObjectiveSelectionsUseTheirOwnMetrics() {
        var cs=event("CS2",2,1);
        assertWinner(cs,"TOTAL_MAPS","OVER"); assertWinner(cs,"MAP_HANDICAP","AWAY"); assertWinner(cs,"SERIES_SCORE","2_1");
        var valorant=event("VALORANT",1,2); data(valorant,Map.of("pistol1","HOME")); assertWinner(valorant,"PISTOL1","HOME");
        var lol=event("LEAGUE_OF_LEGENDS",2,0); data(lol,Map.of("firstBaron","NONE","killsHome","19","killsAway","8"));
        assertWinner(lol,"FIRST_BARON","NONE"); assertWinner(lol,"MAP1_TOTAL_KILLS","OVER");
        var dota=event("DOTA2",0,2); data(dota,Map.of("firstRoshan","AWAY","killsHome","20","killsAway","25"));
        assertWinner(dota,"FIRST_ROSHAN","AWAY"); assertWinner(dota,"MAP1_TOTAL_KILLS","UNDER");
    }

    @Test
    void racingPaysEachQualifyingDriverAndTheHeadToHeadOnlyComparesSelectedDrivers() {
        var event=event("MOTORSPORT",0,0); var participants=new ArrayList<EventParticipant>();
        for(int i=0;i<6;i++) { var p=new EventParticipant(); var c=new Competitor(); c.setCode("P"+i);c.setName("Piloto "+i);p.setCompetitor(c);p.setPosition(6-i);participants.add(p); }
        engine.validateEvent(event,participants,true);
        var definitions=catalog.definitions(event,participants);
        assertThat(engine.evaluate(find(definitions,"RACE_WINNER"),event,participants).winningKeys()).containsExactly("P5");
        assertThat(engine.evaluate(find(definitions,"TOP3"),event,participants).winningKeys()).containsExactlyInAnyOrder("P3","P4","P5");
        assertThat(engine.evaluate(find(definitions,"TOP5"),event,participants).winningKeys()).containsExactlyInAnyOrder("P1","P2","P3","P4","P5");
        assertThat(engine.evaluate(find(definitions,"DRIVER_DUEL"),event,participants).winningKeys()).containsExactly("P1");
        participants.getFirst().setPosition(1);
        assertThatThrownBy(() -> engine.validateEvent(event,participants,true)).hasMessageContaining("sem repetir");
    }

    @Test
    void resultInputRejectsUnknownKeysInvalidNumbersAndUnlistedSelections() {
        var event=event("TENNIS",2,0); var definition=definition(event,"TOTAL_GAMES");
        assertThatThrownBy(() -> engine.evaluate(definition,event,List.of())).hasMessageContaining("Informe");
        assertThatThrownBy(() -> engine.storeData(event,Map.of("unknown","5"),definition.fields())).hasMessageContaining("não suportada");
        for(String invalid:List.of("1.5","-1","10001","NaN"))
            assertThatThrownBy(() -> engine.storeData(event,Map.of("gamesHome",invalid),definition.fields())).hasMessageContaining("inteiro");
        assertThatThrownBy(() -> engine.storeData(event,Map.of("tieBreak","MAYBE"),definition(event,"TIEBREAK").fields())).hasMessageContaining("Seleção inválida");
        engine.storeData(event,Map.of("gamesHome"," 12 ","gamesAway","8"),definition.fields());
        engine.storeData(event,Map.of("gamesAway","4"),definition.fields());
        assertThat(engine.data(event)).containsEntry("gamesHome","12").containsEntry("gamesAway","4");
    }

    @Test
    void storedDefinitionDoesNotFollowSubsequentCatalogContextChanges() {
        var event=event("TENNIS",2,1); var market=new PredictionMarket(); market.setEvent(event);market.setTemplateCode("TOTAL_SETS");
        catalog.snapshot(market,definition(event,"TOTAL_SETS")); event.setBestOf(5);
        assertThat(catalog.definition(market,List.of()).orElseThrow().line()).isEqualByComparingTo("2.5");
        assertThat(definition(event,"TOTAL_SETS").line()).isEqualByComparingTo("3.5");
    }

    private ArenaEvent event(String code,int home,int away) {
        var sport=new Sport();sport.setCode(code);var championship=new Championship();championship.setSport(sport);
        var event=new ArenaEvent();event.setChampionship(championship);event.setHomeScore(home);event.setAwayScore(away);event.setBestOf(3);return event;
    }
    private Definition definition(ArenaEvent event,String code) { return find(catalog.definitions(event,List.of()),code); }
    private Definition find(List<Definition> definitions,String code) { return definitions.stream().filter(d -> d.code().equals(code)).findFirst().orElseThrow(); }
    private void assertWinner(ArenaEvent event,String code,String key) {
        var outcome=engine.evaluate(definition(event,code),event,List.of());
        assertThat(outcome.refund()).as(code).isFalse(); assertThat(outcome.winningKeys()).as(code).containsExactly(key);
    }
    private void data(ArenaEvent event,Map<String,String> values) { try { event.setResultData(json.writeValueAsString(values)); } catch(Exception e) { throw new IllegalStateException(e); } }
}
