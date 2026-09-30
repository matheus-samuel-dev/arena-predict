package com.bolao.copa.arena.service.provider.pandascore;

import static org.assertj.core.api.Assertions.assertThat;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.service.provider.SportsMatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PandaScoreMapperTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final PandaScoreMapper mapper = new PandaScoreMapper();

    @Test
    void mapsUpcomingFixtureAndUtcWithoutInventingAScore() throws Exception {
        SportsMatch match = map(fixture("upcoming-matches.json"), true);
        assertThat(match.externalId()).isEqualTo("900001");
        assertThat(match.status()).isEqualTo(EventStatus.SCHEDULED);
        assertThat(match.scheduledAt()).isEqualTo(Instant.parse("2026-09-24T21:00:00Z"));
        assertThat(match.homeTeam().name()).isEqualTo("Team Liquid");
        assertThat(match.homeTeam().externalId()).isEqualTo("1001");
        assertThat(match.awayTeam().acronym()).isEqualTo("NAVI");
        assertThat(match.homeTeam().logoUrl()).startsWith("https://cdn.pandascore.co/");
        assertThat(match.championship().name()).isEqualTo("IEM Cologne 2026");
        assertThat(match.championship().leagueName()).isEqualTo("Intel Extreme Masters");
        assertThat(match.championship().externalId()).isEqualTo("5001");
        assertThat(match.championship().season()).isEqualTo("2026");
        assertThat(match.bestOf()).isEqualTo(3);
        assertThat(match.homeScore()).isNull();
        assertThat(match.awayScore()).isNull();
        assertThat(match.winnerExternalId()).isNull();
        assertThat(match.liveScoreAvailable()).isFalse();
    }

    @Test
    void mapsLiveScoreByTeamIdOnlyWhenCapabilityIsEnabled() throws Exception {
        var source = fixture("running-matches.json");
        SportsMatch live = map(source, true);
        assertThat(live.status()).isEqualTo(EventStatus.LIVE);
        assertThat(live.homeScore()).isZero();
        assertThat(live.awayScore()).isEqualTo(1);
        assertThat(live.liveScoreAvailable()).isTrue();
        SportsMatch fixturesOnly = map(source, false);
        assertThat(fixturesOnly.status()).isEqualTo(EventStatus.LIVE);
        assertThat(fixturesOnly.homeScore()).isNull();
        assertThat(fixturesOnly.awayScore()).isNull();
        assertThat(fixturesOnly.liveScoreAvailable()).isFalse();
    }

    @Test
    void mapsFinishedResultEvenWithoutPaidLiveCapability() throws Exception {
        SportsMatch match = map(fixture("finished-match.json"), false);
        assertThat(match.status()).isEqualTo(EventStatus.FINISHED);
        assertThat(match.homeScore()).isEqualTo(2);
        assertThat(match.awayScore()).isEqualTo(1);
        assertThat(match.winnerExternalId()).isEqualTo("1001");
        assertThat(match.endedAt()).isEqualTo(Instant.parse("2026-09-24T23:20:00Z"));
        assertThat(match.liveScoreAvailable()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"not_started,SCHEDULED", "running,LIVE", "finished,FINISHED", "canceled,CANCELLED", "postponed,POSTPONED"})
    void translatesAllDocumentedProviderStatuses(String provider, EventStatus internal) throws Exception {
        assertThat(PandaScoreStatusMapper.map(provider)).contains(internal);
        ObjectNode source = fixture("finished-match.json");
        source.put("status", provider);
        assertThat(map(source, false).status()).isEqualTo(internal);
    }

    @Test
    void rejectsUnknownStatusOtherGameAndMissingId() throws Exception {
        assertThat(PandaScoreStatusMapper.map(null)).isEmpty();
        assertThat(PandaScoreStatusMapper.map("suspended_unknown")).isEmpty();
        var source = fixture("finished-match.json");
        source.put("status", "suspended_unknown");
        assertThat(mapper.match(json.treeToValue(source, PandaScoreDtos.Match.class), false)).isEmpty();
        source.put("status", "finished");
        source.set("videogame", json.readTree("{\"id\":1,\"slug\":\"lol\",\"name\":\"League of Legends\"}"));
        assertThat(mapper.match(json.treeToValue(source, PandaScoreDtos.Match.class), false)).isEmpty();
        source.remove("id");
        assertThat(mapper.match(json.treeToValue(source, PandaScoreDtos.Match.class), false)).isEmpty();
    }

    @Test
    void missingOptionalDataStaysNull() throws Exception {
        var source = fixture("finished-match.json");
        source.remove(java.util.List.of("results", "opponents", "tournament", "league", "serie", "winner", "winner_id", "number_of_games", "scheduled_at", "begin_at"));
        SportsMatch match = map(source, true);
        assertThat(match.homeTeam()).isNull();
        assertThat(match.awayTeam()).isNull();
        assertThat(match.championship()).isNull();
        assertThat(match.homeScore()).isNull();
        assertThat(match.awayScore()).isNull();
        assertThat(match.winnerExternalId()).isNull();
        assertThat(match.bestOf()).isNull();
        assertThat(match.scheduledAt()).isNull();
    }

    @Test
    void acceptsProvidedBeginAtAsScheduleFallbackAndDoesNotInferWinner() throws Exception {
        var source = fixture("finished-match.json");
        source.remove(java.util.List.of("scheduled_at", "winner_id", "winner"));
        SportsMatch match = map(source, false);
        assertThat(match.scheduledAt()).isEqualTo(Instant.parse("2026-09-24T21:04:00Z"));
        assertThat(match.winnerExternalId()).isNull();
    }

    @Test
    void preservesForfeitAndDrawForSafeSettlementDecision() throws Exception {
        var source = fixture("finished-match.json");
        source.put("forfeit", true);
        source.put("draw", true);
        SportsMatch match = map(source, false);
        assertThat(match.forfeit()).isTrue();
        assertThat(match.draw()).isTrue();
    }

    @Test
    void rejectsAmbiguousNegativeAndAbsentScoresWithoutAssumingZero() throws Exception {
        var source = fixture("running-matches.json");
        source.set("results", json.readTree("[{\"team_id\":1001,\"score\":1},{\"team_id\":1001,\"score\":2},{\"team_id\":1002,\"score\":-1}]"));
        var match = map(source, true);
        assertThat(match.homeScore()).isNull();
        assertThat(match.awayScore()).isNull();
        assertThat(match.liveScoreAvailable()).isFalse();
        source.set("results", json.readTree("[{\"team_id\":1001,\"score\":null},{\"team_id\":555,\"score\":2}]"));
        assertThat(map(source, true).homeScore()).isNull();
        assertThat(map(source, true).awayScore()).isNull();
    }

    @Test
    void invalidDuplicateScoreStillMakesTheTeamResultAmbiguous() throws Exception {
        var source = fixture("running-matches.json");
        source.set("results", json.readTree("[{\"team_id\":1001,\"score\":1},{\"team_id\":1001,\"score\":null},{\"team_id\":1002,\"score\":0}]"));
        var match = map(source, true);
        assertThat(match.homeScore()).isNull();
        assertThat(match.awayScore()).isZero();
        assertThat(match.liveScoreAvailable()).isFalse();
    }

    @Test
    void contradictoryOrInvalidWinnerDoesNotBecomeAConfirmedResult() throws Exception {
        var source = fixture("finished-match.json");
        source.put("winner_id", 1002);
        assertThat(map(source, false).winnerExternalId()).isNull();
        source.remove("winner");
        source.put("winner_id", -1);
        assertThat(map(source, false).winnerExternalId()).isNull();
    }

    @Test
    void moreThanTwoOpponentsCannotSilentlyChooseAnArbitraryPair() throws Exception {
        var source = fixture("upcoming-matches.json");
        source.withArray("opponents").add(json.readTree("{\"type\":\"Team\",\"opponent\":{\"id\":1003,\"name\":\"Team Spirit\"}}"));
        assertThat(mapper.match(json.treeToValue(source, PandaScoreDtos.Match.class), false)).isEmpty();
    }

    @Test
    void unsupportedFormatIsNotInvented() throws Exception {
        var source = fixture("upcoming-matches.json");
        source.put("match_type", "first_to");
        assertThat(map(source, false).bestOf()).isNull();
        source.put("match_type", "best_of");
        source.put("number_of_games", 2);
        assertThat(map(source, false).bestOf()).isNull();
    }

    @Test
    void unsafeMissingAndMalformedLogoUrlsBecomeFallbacks() {
        for (String url : new String[]{null, "", "javascript:alert(1)", "http://example.com/logo.png", "https://user:password@example.com/a", "https://", "not a url"}) {
            assertThat(mapper.team(new PandaScoreDtos.Team(1L, "FURIA Esports", "FUR", url)).logoUrl()).isNull();
        }
        assertThat(mapper.team(new PandaScoreDtos.Team(null, "FURIA Esports", null, null))).isNull();
        assertThat(mapper.team(new PandaScoreDtos.Team(-1L, "FURIA Esports", null, null))).isNull();
    }

    private SportsMatch map(ObjectNode source, boolean live) throws Exception {
        return mapper.match(json.treeToValue(source, PandaScoreDtos.Match.class), live).orElseThrow();
    }

    private ObjectNode fixture(String name) throws IOException {
        var node = json.readTree(readFixture(name));
        return (ObjectNode) (node.isArray() ? node.get(0) : node);
    }

    static String readFixture(String name) throws IOException {
        try (var stream = PandaScoreMapperTest.class.getResourceAsStream("/pandascore/" + name)) {
            if (stream == null) throw new IOException("Missing PandaScore fixture " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
