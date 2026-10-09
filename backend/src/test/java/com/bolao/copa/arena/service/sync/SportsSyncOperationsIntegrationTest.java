package com.bolao.copa.arena.service.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.contains;

import com.bolao.copa.arena.api.SportsSyncPublicController;
import com.bolao.copa.arena.config.SportsSyncProperties;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.ArenaCatalogService;
import com.bolao.copa.arena.service.ArenaDashboardService;
import com.bolao.copa.arena.service.provider.*;
import com.bolao.copa.arena.service.provider.pandascore.*;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.security.JwtService;
import com.bolao.copa.support.RegularTestUsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Fixtures only, rolled back after every test. Never contacts PandaScore or populates a running deployment. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class SportsSyncOperationsIntegrationTest {
    private static final Instant NOW=Instant.parse("2026-10-05T12:00:00Z");
    @Autowired SportsSyncStateStore state;
    @Autowired SportsCatalogSyncService catalog;
    @Autowired SportsMatchSyncService matches;
    @Autowired ArenaEventRepository events;
    @Autowired CompetitorRepository teams;
    @Autowired ArenaCatalogService arena;
    @Autowired ArenaDashboardService dashboard;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @Autowired MockMvc http;

    @ParameterizedTest
    @CsvSource({"csgo,CS2,901001", "lol,LEAGUE_OF_LEGENDS,901002", "valorant,VALORANT,901003"})
    void fixturesTravelThroughMapperSyncDatabaseApiAndTheSameDashboardLiveSource(String game,String code,long id) throws Exception {
        SportsMatch source=fixture(game,id);
        SportsDataProvider provider=provider(source);
        long before=events.count();
        var sync=coordinator(provider,NOW);
        sync.scheduledSynchronize();
        var stored=events.findByExternalProviderAndExternalId("PANDASCORE",Long.toString(id)).orElseThrow();
        assertThat(events.count()).isEqualTo(before+1);
        assertThat(stored.getStatus()).isEqualTo(EventStatus.LIVE);
        assertThat(stored.getChampionship().getSport().getCode()).isEqualTo(code);
        assertThat(stored.getHomeCompetitor().getSport().getCode()).isEqualTo(code);
        assertThat(stored.getHomeScore()).isNull();
        assertThat(stored.getAwayScore()).isNull();
        assertThat(stored.isDemo()).isFalse();
        assertThat(arena.eventResponse(stored).markets()).singleElement().satisfies(market->{
            assertThat(market.templateCode()).isEqualTo("SERIES_WINNER_LIVE");
            assertThat(market.timingMode()).isEqualTo(MarketTimingMode.LIVE_ONLY);
            assertThat(market.availability().allowed()).isFalse();
            assertThat(market.availability().code()).isEqualTo("PRICING_DATA_REQUIRED");
            assertThat(market.pricing().confidence()).isEqualTo("NONE");
            assertThat(market.options()).hasSize(2);
        });
        assertThat(state.snapshot("PANDASCORE").inserted()).isEqualTo(1);
        assertThat(state.snapshot("PANDASCORE").updated()).isZero();
        assertThat(state.snapshot("PANDASCORE").schedulerTickAt()).isEqualTo(NOW);
        assertThat(sync.summary().healthy()).isTrue();

        var user=RegularTestUsers.participant(users);
        String authorization="Bearer "+jwt.generate(user);
        http.perform(get("/api/events/live").header("Authorization",authorization)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.externalId == '"+id+"')].status").value(contains("LIVE")));
        assertThat(dashboard.dashboard(user).liveEvents()).extracting(event -> event.id())
                .containsExactlyElementsOf(arena.liveEvents().stream().map(event -> event.id()).toList());

        // Two feeds may receive the same ID; counters and persistence still represent one record.
        coordinator(provider,NOW.plusSeconds(121)).scheduledSynchronize();
        assertThat(events.count()).isEqualTo(before+1);
        var metrics=state.snapshot("PANDASCORE");
        assertThat(metrics.inserted()).isZero();
        assertThat(metrics.updated()).isZero();
        assertThat(metrics.skipped()).isEqualTo(1);
        assertThat(metrics.lastHttpStatus()).isEqualTo(200); // Explicit mocked transport metadata.
        assertThat(metrics.lastSuccessAt()).isEqualTo(NOW.plusSeconds(121));
        String safe=json.writeValueAsString(new SportsSyncPublicController(coordinator(provider,NOW.plusSeconds(121))).status());
        assertThat(safe).contains("healthy","lastSuccessAt").doesNotContain("configured","message","token","lastErrorReason");
    }

    @Test
    void missingProviderLogoPreservesSavedLogoAndRemoteLogoCannotReplaceALocalIdentity() throws Exception {
        var source=fixture("csgo",902001);
        coordinator(provider(source),NOW).synchronize();
        var home=teams.findByExternalProviderAndExternalIdIn("PANDASCORE",List.of(source.homeTeam().externalId())).getFirst();
        String previous=home.getImageUrl();
        var missing=withHome(source,new SportsTeam(source.homeTeam().externalId(),"Updated source name",null,null));
        catalog.synchronize("PANDASCORE",List.of(missing));
        assertThat(home.getImageUrl()).isEqualTo(previous);
        assertThat(home.getName()).isEqualTo("Updated source name");
        home.setImageUrl("/assets/teams/team-liquid.svg"); teams.saveAndFlush(home);
        catalog.synchronize("PANDASCORE",List.of(withHome(source,new SportsTeam(source.homeTeam().externalId(),source.homeTeam().name(),null,"https://cdn.example.test/generic.png"))));
        assertThat(home.getImageUrl()).isEqualTo("/assets/teams/team-liquid.svg");
    }

    @Test
    void acquiringLeaseAndSchedulerHeartbeatNeverInventExternalAttempts() {
        assertThat(state.claim("PANDASCORE","first",NOW,NOW.plusSeconds(900))).isTrue();
        assertThat(state.claim("PANDASCORE","second",NOW,NOW.plusSeconds(900))).isFalse();
        assertThat(state.snapshot("PANDASCORE").lastAttemptAt()).isNull();
        state.release("PANDASCORE","first");
        var provider=provider(null); when(provider.available()).thenReturn(false);
        var sync=coordinator(provider,NOW);
        sync.scheduledSynchronize();
        assertThat(state.snapshot("PANDASCORE").schedulerTickAt()).isEqualTo(NOW);
        assertThat(state.snapshot("PANDASCORE").lastAttemptAt()).isNull();
        assertThat(state.snapshot("PANDASCORE").lastSuccessAt()).isNull();
        assertThat(sync.status().status()).isEqualTo("UNCONFIGURED");
        verify(provider,never()).runningMatches();
    }

    @Test
    void participantStatusContainsNoOperationalDiagnostic() throws Exception {
        String authorization="Bearer "+jwt.generate(RegularTestUsers.participant(users));
        http.perform(get("/api/sports-sync/status").header("Authorization",authorization)).andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(false)).andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.configured").doesNotExist()).andExpect(jsonPath("$.lastErrorReason").doesNotExist());
        http.perform(get("/api/admin/sports-sync/status").header("Authorization",authorization)).andExpect(status().isForbidden());
    }

    private SportsSyncService coordinator(SportsDataProvider provider,Instant now) {
        var props=new SportsSyncProperties("PANDASCORE",true,900000,120000,300000,120000,30,7,72,20,900000);
        return new SportsSyncService(List.of(provider),props,state,catalog,matches,events,Clock.fixed(now,ZoneOffset.UTC));
    }
    private SportsDataProvider provider(SportsMatch source) {
        var provider=mock(SportsDataProvider.class);
        when(provider.providerId()).thenReturn("PANDASCORE"); when(provider.providerName()).thenReturn("PandaScore fixture");
        when(provider.available()).thenReturn(true); when(provider.lastHttpStatus()).thenReturn(200);
        when(provider.supportedSports()).thenReturn(List.of("CS2","LEAGUE_OF_LEGENDS","VALORANT"));
        when(provider.runningMatches()).thenReturn(source==null?List.of():List.of(source));
        when(provider.matchDetails(anyList())).thenReturn(source==null?List.of():List.of(source));
        return provider;
    }
    private SportsMatch fixture(String game,long id) throws Exception {
        try(var input=getClass().getResourceAsStream("/pandascore/running-matches.json")) {
            var source=(ObjectNode)json.readTree(input).get(0);
            source.put("id",id); source.put("scheduled_at",NOW.minusSeconds(60).toString());
            source.set("videogame",json.readTree("{\"slug\":\""+game+"\"}"));
            ((ObjectNode)source.path("tournament")).put("id",id+1000);
            ((ObjectNode)source.path("opponents").get(0).path("opponent")).put("id",id+2000);
            ((ObjectNode)source.path("opponents").get(1).path("opponent")).put("id",id+3000);
            return new PandaScoreMapper().match(json.treeToValue(source,PandaScoreDtos.Match.class),false).orElseThrow();
        }
    }
    private SportsMatch withHome(SportsMatch source,SportsTeam home) {
        return new SportsMatch(source.externalId(),source.title(),home,source.awayTeam(),source.championship(),source.scheduledAt(),source.endedAt(),
                source.status(),source.homeScore(),source.awayScore(),source.bestOf(),source.winnerExternalId(),source.forfeit(),source.draw(),source.liveScoreAvailable(),source.sportCode());
    }
}
