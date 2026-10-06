package com.bolao.copa.arena.service.provider.pandascore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bolao.copa.arena.service.provider.SportsProviderException;
import com.bolao.copa.arena.service.provider.SportsProviderException.Reason;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PandaScoreSportsDataProviderTest {
    private static final Instant NOW = Instant.parse("2026-09-24T21:00:00Z");
    private final PandaScoreClient client = mock(PandaScoreClient.class);
    private final PandaScoreProperties properties = new PandaScoreProperties();
    private final PandaScoreClientTest.MutableClock clock = new PandaScoreClientTest.MutableClock(NOW);
    private final PandaScoreSportsDataProvider provider = new PandaScoreSportsDataProvider(client, new PandaScoreMapper(), properties, clock);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void upcomingUsesCsgoEndpointWithUtcWindowAndCollapsesRepeatedIds() throws Exception {
        var match = upcoming();
        Map<String, String> query = Map.of("range[scheduled_at]", NOW + "," + NOW.plusSeconds(3600), "sort", "scheduled_at,id");
        when(client.list("/csgo/matches/upcoming", query, PandaScoreDtos.Match.class)).thenReturn(List.of(match, match));
        assertThat(provider.upcomingMatches(NOW, NOW.plusSeconds(3600))).hasSize(1);
        verify(client).list("/csgo/matches/upcoming", query, PandaScoreDtos.Match.class);
    }

    @Test
    void runningAndPastUseDedicatedFixtureEndpoints() throws Exception {
        var match = upcoming();
        when(client.list(eq("/csgo/matches/running"), anyMap(), eq(PandaScoreDtos.Match.class))).thenReturn(List.of(match));
        var query = Map.of("range[end_at]", NOW.minusSeconds(3600) + "," + NOW, "sort", "-end_at,id");
        when(client.list("/csgo/matches/past", query, PandaScoreDtos.Match.class)).thenReturn(List.of(match));
        assertThat(provider.runningMatches()).hasSize(1);
        assertThat(provider.finishedMatches(NOW.minusSeconds(3600))).hasSize(1);
        verify(client).list("/csgo/matches/past", query, PandaScoreDtos.Match.class);
    }

    @Test
    void detailsUseGenericAllPlansEndpointAndKeepMissingMatchEmpty() throws Exception {
        var finalMatch = json.readValue(PandaScoreMapperTest.readFixture("finished-match.json"), PandaScoreDtos.Match.class);
        when(client.detail("/matches/900001", PandaScoreDtos.Match.class)).thenReturn(finalMatch);
        assertThat(provider.matchDetails("900001")).hasValueSatisfying(match -> assertThat(match.homeScore()).isEqualTo(2));
        when(client.detail("/matches/900002", PandaScoreDtos.Match.class))
                .thenThrow(new SportsProviderException(Reason.NOT_FOUND, "missing", null));
        assertThat(provider.matchDetails("900002")).isEmpty();
    }

    @Test
    void invalidDetailIdCannotBeUsedAsUrlPath() {
        // The constructor reads availability once; clear that benign interaction.
        org.mockito.Mockito.clearInvocations(client);
        for (String value : new String[]{"../teams", "1?token=secret", "0", "-1", "abc", ""}) {
            assertThatThrownBy(() -> provider.matchDetails(value)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(client);
    }

    @Test
    void bulkDetailsDeduplicateAndBatchIdsInsteadOfCallingDetailsPerMatch() throws Exception {
        properties.setPageSize(2);
        when(client.list(eq("/matches"), anyMap(), eq(PandaScoreDtos.Match.class))).thenReturn(List.of(upcoming()));
        provider.matchDetails(List.of("900001", "900001", "900002", "900003"));
        verify(client).list("/matches", Map.of("filter[id]", "900001,900002", "sort", "id"), PandaScoreDtos.Match.class);
        verify(client).list("/matches", Map.of("filter[id]", "900003", "sort", "id"), PandaScoreDtos.Match.class);
        verify(client, times(2)).list(eq("/matches"), anyMap(), eq(PandaScoreDtos.Match.class));
    }

    @Test
    void runningFeedUsesAllConfiguredGameEndpointsWithoutMislabelingTheirSport() throws Exception {
        for (String game:List.of("csgo","lol","valorant")) {
            var tree=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(PandaScoreMapperTest.readFixture("running-matches.json")).get(0);
            tree.put("id",game.equals("csgo")?901001:game.equals("lol")?901002:901003);
            tree.set("videogame",json.readTree("{\"slug\":\""+game+"\"}"));
            when(client.list(eq("/"+game+"/matches/running"),anyMap(),eq(PandaScoreDtos.Match.class)))
                    .thenReturn(List.of(json.treeToValue(tree,PandaScoreDtos.Match.class)));
        }
        assertThat(provider.runningMatches()).extracting(match -> match.sportCode()).containsExactly("CS2","LEAGUE_OF_LEGENDS","VALORANT");
        assertThat(provider.supportedSports()).containsExactly("CS2","LEAGUE_OF_LEGENDS","VALORANT");
    }

    @Test
    void unknownExternalStateCannotBecomeAHealthyEmptyFeed() throws Exception {
        var tree=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(PandaScoreMapperTest.readFixture("running-matches.json")).get(0);
        tree.put("status","unknown_future_state");
        when(client.list(eq("/csgo/matches/running"),anyMap(),eq(PandaScoreDtos.Match.class)))
                .thenReturn(List.of(json.treeToValue(tree,PandaScoreDtos.Match.class)));
        assertThatThrownBy(provider::runningMatches).isInstanceOfSatisfying(SportsProviderException.class,error -> {
            assertThat(error.getReason()).isEqualTo(Reason.INVALID_RESPONSE);
            assertThat(error.getRetryAt()).isEqualTo(NOW.plusMillis(properties.getFailureBackoffMs()));
        });
    }

    @Test
    void teamReferenceCacheAvoidsRepeatedRequestsAndRefreshesAfterTtl() {
        properties.setReferenceCacheTtlMs(60000);
        when(client.list(eq("/csgo/teams"), anyMap(), eq(PandaScoreDtos.Team.class)))
                .thenReturn(List.of(new PandaScoreDtos.Team(1L, "Team Liquid", "TL", null)))
                .thenReturn(List.of(new PandaScoreDtos.Team(1L, "Liquid", "TL", null)));
        assertThat(provider.teams().getFirst().name()).isEqualTo("Team Liquid");
        assertThat(provider.teams().getFirst().name()).isEqualTo("Team Liquid");
        verify(client, times(1)).list(eq("/csgo/teams"), anyMap(), eq(PandaScoreDtos.Team.class));
        clock.advance(Duration.ofMinutes(1));
        assertThat(provider.teams().getFirst().name()).isEqualTo("Liquid");
        verify(client, times(2)).list(eq("/csgo/teams"), anyMap(), eq(PandaScoreDtos.Team.class));
    }

    @Test
    void optionalReferenceCacheCoversAllConfiguredGamesWithoutRepeatingCalls() {
        int id=1;
        for(String game:List.of("csgo","lol","valorant"))
            when(client.list(eq("/"+game+"/teams"),anyMap(),eq(PandaScoreDtos.Team.class)))
                    .thenReturn(List.of(new PandaScoreDtos.Team((long)id++,game,null,null)));
        assertThat(provider.teams()).hasSize(3);
        assertThat(provider.teams()).hasSize(3);
        for(String game:List.of("csgo","lol","valorant"))
            verify(client,times(1)).list(eq("/"+game+"/teams"),anyMap(),eq(PandaScoreDtos.Team.class));
    }

    @Test
    void championshipReferenceCacheUsesEmbeddedLeagueAndSeriesWithoutFanOut() {
        var tournament = new PandaScoreDtos.Tournament(3L, "Playoffs", "playoffs", null, null,
                new PandaScoreDtos.League(4L, "IEM", null), new PandaScoreDtos.Series(5L, "Cologne", "IEM Cologne 2026", null, 2026));
        when(client.list(eq("/csgo/tournaments/upcoming"), anyMap(), eq(PandaScoreDtos.Tournament.class))).thenReturn(List.of(tournament));
        assertThat(provider.championships().getFirst().name()).isEqualTo("IEM Cologne 2026");
        provider.championships();
        verify(client, times(1)).list(eq("/csgo/tournaments/upcoming"), anyMap(), eq(PandaScoreDtos.Tournament.class));
    }

    @Test
    void propagatesTemporaryFailureForSchedulerWhileExposingSafeHealthMetadata() {
        when(client.configured()).thenReturn(true);
        when(client.nextAllowedRequestAt()).thenReturn(NOW.plusSeconds(60));
        when(client.remainingRequests()).thenReturn(500L);
        when(client.list(eq("/csgo/matches/running"), anyMap(), eq(PandaScoreDtos.Match.class)))
                .thenThrow(new SportsProviderException(Reason.UNAVAILABLE, "temporarily unavailable", NOW.plusSeconds(60)));
        assertThat(provider.available()).isTrue();
        assertThat(provider.demo()).isFalse();
        assertThat(provider.providerId()).isEqualTo("PANDASCORE");
        assertThat(provider.nextAllowedRequestAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(provider.remainingRequests()).isEqualTo(500L);
        assertThatThrownBy(provider::runningMatches).isInstanceOf(SportsProviderException.class);
    }

    private PandaScoreDtos.Match upcoming() throws Exception {
        return json.readValue(PandaScoreMapperTest.readFixture("upcoming-matches.json"), PandaScoreDtos.Match[].class)[0];
    }
}
