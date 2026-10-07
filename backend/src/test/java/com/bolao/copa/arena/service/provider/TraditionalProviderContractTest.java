package com.bolao.copa.arena.service.provider;

import static org.assertj.core.api.Assertions.*;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;

/** Official schema fixtures only. No external HTTP or production credential is used. */
class TraditionalProviderContractTest {
    private final ObjectMapper json=new ObjectMapper();
    private final MockEnvironment env=new MockEnvironment();
    @ParameterizedTest @CsvSource({"1H,LIVE","HT,LIVE","2H,LIVE","ET,LIVE","P,LIVE","NS,SCHEDULED","TBD,SCHEDULED","FT,FINISHED","AET,FINISHED","PEN,FINISHED","PST,POSTPONED","INT,POSTPONED","SUSP,POSTPONED","CANC,CANCELLED","ABD,CANCELLED","AWD,CANCELLED","WO,CANCELLED"})
    void footballStatusContract(String raw,EventStatus expected) {assertThat(ApiFootballProvider.status(raw)).isEqualTo(expected);}
    @ParameterizedTest @CsvSource({"NS,SCHEDULED","Q1,LIVE","Q2,LIVE","Q3,LIVE","Q4,LIVE","OT,LIVE","BT,LIVE","HT,LIVE","FT,FINISHED","AOT,FINISHED","POST,POSTPONED","SUSP,POSTPONED","CANC,CANCELLED","ABD,CANCELLED","AWD,CANCELLED"})
    void basketballStatusContract(String raw,EventStatus expected) {assertThat(ApiBasketballProvider.status(raw)).isEqualTo(expected);}
    @ParameterizedTest @CsvSource({"Finished,false,FINISHED","Set 1,true,LIVE","Scheduled,false,SCHEDULED","Postponed,false,POSTPONED","Retired,false,CANCELLED","Walkover,false,CANCELLED"})
    void tennisStatusContract(String raw,boolean live,EventStatus expected) {assertThat(ApiTennisProvider.status(raw,live)).isEqualTo(expected);}
    @ParameterizedTest @CsvSource({"Live,LIVE","Scheduled,SCHEDULED","Completed,FINISHED","Cancelled,CANCELLED","Postponed,POSTPONED"})
    void raceStatusContract(String raw,EventStatus expected) {assertThat(ApiFormula1Provider.status(raw)).isEqualTo(expected);}
    @Test void missingKeysNeverInventSuccessOrMakeAnHttpRequest() {
        var providers=List.of(new ApiFootballProvider(env,json),new ApiBasketballProvider(env,json),new ApiTennisProvider(env,json),new ApiFormula1Provider(env,json));
        for(var provider:providers) {
            assertThat(provider.available()).isFalse();assertThat(provider.enabled()).isFalse();assertThat(provider.lastHttpStatus()).isNull();
            assertThatThrownBy(provider::runningMatches).isInstanceOf(SportsProviderException.class)
                    .extracting("reason").isEqualTo(SportsProviderException.Reason.NOT_CONFIGURED);
        }
    }
    @Test void unknownStatusesFailClosedInsteadOfBeingSilentlyMappedToScheduled() {
        assertThatThrownBy(()->ApiFootballProvider.status("NEW_STATUS")).isInstanceOf(SportsProviderException.class);
        assertThatThrownBy(()->ApiBasketballProvider.status("IN_PROGRESS_UNKNOWN")).isInstanceOf(SportsProviderException.class);
        assertThatThrownBy(()->ApiTennisProvider.status("Unrecognized",false)).isInstanceOf(SportsProviderException.class);
        assertThatThrownBy(()->ApiFormula1Provider.status("Unknown")).isInstanceOf(SportsProviderException.class);
    }
    @Test void footballNeverSettlesRegulationContractsUsingShootoutTotals() throws Exception {
        var source=json.readTree("""
                {"fixture":{"id":101,"date":"2026-10-06T18:00:00+00:00","status":{"short":"PEN","elapsed":120}},
                 "league":{"id":1,"name":"Contract fixture","season":2026},"teams":{"home":{"id":10,"name":"Home"},"away":{"id":20,"name":"Away"}},
                 "goals":{"home":3,"away":3},"score":{"fulltime":{"home":1,"away":1},"halftime":{"home":0,"away":1},"penalty":{"home":5,"away":4}}}
                """);
        var match=new ApiFootballProvider(env,json).map(source);
        assertThat(match.status()).isEqualTo(EventStatus.FINISHED);assertThat(match.homeScore()).isEqualTo(1);assertThat(match.awayScore()).isEqualTo(1);
        assertThat(match.draw()).isTrue();assertThat(match.resultData()).containsEntry("firstHalfAway","1");
        assertThat(match.resultData()).containsEntry("fullMatchHome","3").containsEntry("penaltiesHome","5");
        assertThat(match.bestOf()).isNull();assertThat(match.winnerExternalId()).isNull();
    }
    @Test void liveExtraTimeShowsActualGoalsWhileRegulationContractsRemainSeparate() throws Exception {
        var source=json.readTree("""
                {"fixture":{"id":104,"date":"2026-10-06T18:00:00+00:00","status":{"short":"ET","elapsed":105}},
                 "league":{"id":1,"name":"Contract fixture"},"teams":{"home":{"id":10,"name":"Home"},"away":{"id":20,"name":"Away"}},
                 "goals":{"home":3,"away":2},"score":{"fulltime":{"home":1,"away":1}}}
                """);
        var match=new ApiFootballProvider(env,json).map(source);
        assertThat(match.status()).isEqualTo(EventStatus.LIVE);assertThat(match.homeScore()).isEqualTo(3);assertThat(match.awayScore()).isEqualTo(2);
        assertThat(match.period()).isEqualTo("Prorrogação");assertThat(match.winnerExternalId()).isNull();
    }
    @Test void basketballUsesTotalIncludingOvertimeAndPreservesPeriods() throws Exception {
        var source=json.readTree("""
                {"id":102,"date":"2026-10-06T18:00:00+00:00","status":{"short":"AOT"},"league":{"id":12,"name":"NBA","season":"2026-2027"},
                 "teams":{"home":{"id":10,"name":"Home"},"away":{"id":20,"name":"Away"}},
                 "scores":{"home":{"quarter_1":20,"quarter_2":30,"quarter_3":25,"quarter_4":25,"over_time":10,"total":110},
                           "away":{"quarter_1":30,"quarter_2":20,"quarter_3":25,"quarter_4":25,"over_time":8,"total":108}}}
                """);
        var match=new ApiBasketballProvider(env,json).map(source);
        assertThat(match.homeScore()).isEqualTo(110);assertThat(match.awayScore()).isEqualTo(108);
        assertThat(match.resultData()).containsEntry("firstHalfHome","50").containsEntry("quarter1Away","30");
        assertThat(match.winnerExternalId()).isEqualTo("10");assertThat(match.bestOf()).isNull();
    }
    @Test void tennisDistinguishesSetsGamesAndDoesNotGuessBestOf() throws Exception {
        var source=json.readTree("""
                {"event_key":"103","event_date":"2026-10-06","event_time":"18:00","event_status":"Finished","event_live":"0",
                 "event_first_player":"Player one","first_player_key":"10","event_second_player":"Player two","second_player_key":"20",
                 "event_final_result":"2 - 0","event_winner":"First Player","event_type_type":"Atp Singles","tournament_key":"1","tournament_name":"Contract fixture",
                 "scores":[{"score_set":"1","score_first":"6","score_second":"4"},{"score_set":"2","score_first":"6","score_second":"2"}]}
                """);
        var match=new ApiTennisProvider(env,json).map(source);
        assertThat(match.homeScore()).isEqualTo(2);assertThat(match.awayScore()).isZero();assertThat(match.bestOf()).isNull();
        assertThat(match.resultData()).containsEntry("gamesHome","12").containsEntry("gamesAway","6");
        assertThat(match.scheduledAt().toString()).isEqualTo("2026-10-06T18:00:00Z");
    }
    @Test void raceDnfDoesNotBecomeAnInventedFinalPosition() throws Exception {
        var provider=new ApiFormula1Provider(env,json);
        var participants=provider.classification(json.readTree("""
                [{"driver":{"id":1,"name":"First"},"position":1,"time":"1:20:00"},{"driver":{"id":2,"name":"Second"},"position":0,"time":"DNF"}]
                """));
        assertThat(participants.get(1).position()).isNull();assertThat(participants.get(1).scoreLabel()).isEqualTo("DNF");
        assertThat(provider.resultMetrics("MOTORSPORT")).isEmpty(); // No contract promises complete classified positions yet.
    }
    @Test void registryCanonicalizesAliasesAndRetainsIndependentProviderIdentities() {
        var registry=new SportsProviderRegistry(List.of(new ApiFootballProvider(env,json),new ApiBasketballProvider(env,json),new ApiTennisProvider(env,json)));
        assertThat(registry.getProvider("football")).get().extracting(SportsDataProvider::providerId).isEqualTo("API_FOOTBALL");
        assertThat(registry.getProvider("VOLLEYBALL")).isEmpty();assertThat(SportsProviderRegistry.canonicalSport("LOL")).isEqualTo("LEAGUE_OF_LEGENDS");
    }
}
