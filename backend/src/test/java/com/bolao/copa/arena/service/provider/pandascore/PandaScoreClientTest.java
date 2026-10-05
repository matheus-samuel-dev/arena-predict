package com.bolao.copa.arena.service.provider.pandascore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bolao.copa.arena.service.provider.SportsProviderException;
import com.bolao.copa.arena.service.provider.SportsProviderException.Reason;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class PandaScoreClientTest {
    private static final String TOKEN = "test-only-credential-do-not-log";
    private static final Instant NOW = Instant.parse("2026-09-24T21:00:00Z");
    private final PandaScoreProperties properties = new PandaScoreProperties();
    private final MutableClock clock = new MutableClock(NOW);
    private MockRestServiceServer server;
    private PandaScoreClient client;

    @BeforeEach
    void setUp() {
        properties.setApiToken(TOKEN);
        properties.setRetryBackoffMs(1);
        var builder = RestClient.builder().baseUrl("https://api.pandascore.co");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PandaScoreClient(properties, new ObjectMapper().findAndRegisterModules(), builder.build(), clock);
    }

    @Test
    void usesServerSideBearerHeaderAndPaginatesWithoutCredentialInUrl() throws Exception {
        properties.setPageSize(1);
        for (int page = 1; page <= 2; page++) {
            int expectedPage = page;
            server.expect(request -> {
                assertThat(request.getURI().getPath()).isEqualTo("/csgo/matches/upcoming");
                assertThat(request.getURI().getQuery()).contains("page=" + expectedPage, "per_page=1", "sort=id").doesNotContain(TOKEN, "token=");
            }).andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                    .andRespond(withSuccess(page == 1 ? PandaScoreMapperTest.readFixture("upcoming-matches.json") : "[]", MediaType.APPLICATION_JSON));
        }
        var result = client.list("/csgo/matches/upcoming", Map.of("sort", "id"), PandaScoreDtos.Match.class);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(900001L);
        server.verify();
    }

    @Test
    void respectsPaginationCap() {
        properties.setPageSize(1);
        properties.setMaxPages(2);
        server.expect(ExpectedCount.times(2), request -> { }).andRespond(withSuccess("[{\"id\":1}]", MediaType.APPLICATION_JSON));
        assertThat(client.list("/csgo/teams", Map.of(), PandaScoreDtos.Team.class)).hasSize(2);
        server.verify();
    }

    @Test
    void missingTokenMakesNoHttpRequest() {
        properties.setApiToken(" ");
        assertThat(client.configured()).isFalse();
        assertThat(failure().getReason()).isEqualTo(Reason.NOT_CONFIGURED);
        assertThat(client.nextAllowedRequestAt()).isNull();
        server.verify();
    }

    @Test
    void rateLimitHonorsRetryAfterAndDoesNotRetryImmediately() {
        server.expect(requestTo("https://api.pandascore.co/matches/1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "120"));
        var first = failure();
        assertThat(first.getReason()).isEqualTo(Reason.RATE_LIMITED);
        assertThat(first.getRetryAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(client.nextAllowedRequestAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(failure().getRetryAt()).isEqualTo(first.getRetryAt());
        clock.advance(Duration.ofSeconds(119));
        assertThat(failure().getReason()).isEqualTo(Reason.RATE_LIMITED);
        server.verify();
    }

    @Test
    void acceptsHttpDateRetryAfter() {
        server.expect(requestTo("https://api.pandascore.co/matches/1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "Thu, 24 Sep 2026 21:02:00 GMT"));
        assertThat(failure().getRetryAt()).isEqualTo(NOW.plusSeconds(120));
        server.verify();
    }

    @Test
    void missingRetryAfterUsesConservativeHourCooldown() {
        server.expect(requestTo("https://api.pandascore.co/matches/1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "unknown"));
        assertThat(failure().getRetryAt()).isEqualTo(NOW.plusSeconds(3600));
        server.verify();
    }

    @Test
    void preservesSuccessfulResponseButPausesAtQuotaReserve() {
        server.expect(requestTo("https://api.pandascore.co/matches/1"))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON).header("X-Rate-Limit-Remaining", "20"));
        assertThat(client.detail("/matches/1", PandaScoreDtos.Match.class).id()).isEqualTo(1L);
        assertThat(client.remainingRequests()).isEqualTo(20L);
        assertThat(failure().getReason()).isEqualTo(Reason.RATE_LIMITED);
        server.verify();
    }

    @Test
    void localBudgetIncludesRetriesAndExpiresAfterAnHour() {
        properties.setMaxRequestsPerHour(1);
        server.expect(ExpectedCount.times(2), requestTo("https://api.pandascore.co/matches/1"))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));
        client.detail("/matches/1", PandaScoreDtos.Match.class);
        assertThat(failure().getRetryAt()).isEqualTo(NOW.plusSeconds(3600));
        clock.advance(Duration.ofHours(1));
        assertThat(client.nextAllowedRequestAt()).isNull();
        client.detail("/matches/1", PandaScoreDtos.Match.class);
        server.verify();
    }

    @Test
    void retryConsumesBudgetSoCannotExceedIt() {
        properties.setMaxRequestsPerHour(1);
        server.expect(requestTo("https://api.pandascore.co/matches/1"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(failure().getReason()).isEqualTo(Reason.RATE_LIMITED);
        server.verify();
    }

    @Test
    void retriesTemporaryServerErrorThenSucceeds() {
        server.expect(requestTo("https://api.pandascore.co/matches/1")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.pandascore.co/matches/1")).andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));
        assertThat(client.detail("/matches/1", PandaScoreDtos.Match.class).id()).isEqualTo(1L);
        assertThat(client.nextAllowedRequestAt()).isNull();
        server.verify();
    }

    @Test
    void serverMaintenanceHonorsRetryAfterWithoutImmediateRetry() {
        server.expect(requestTo("https://api.pandascore.co/matches/1"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "300"));
        var error = failure();
        assertThat(error.getReason()).isEqualTo(Reason.UNAVAILABLE);
        assertThat(error.getRetryAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(failure().getReason()).isEqualTo(Reason.UNAVAILABLE);
        server.verify();
    }

    @Test
    void exhaustedServerRetriesPauseRequestsAndSanitizeError(CapturedOutput output) {
        server.expect(ExpectedCount.times(2), requestTo("https://api.pandascore.co/matches/1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("debug token=" + TOKEN));
        var error = failure();
        assertThat(error.getReason()).isEqualTo(Reason.UNAVAILABLE);
        assertThat(error.getRetryAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(error.getMessage()).doesNotContain(TOKEN, "debug");
        assertThat(error.getCause()).isNull();
        assertThat(failure().getReason()).isEqualTo(Reason.UNAVAILABLE);
        assertThat(output.getAll()).doesNotContain(TOKEN);
        server.verify();
    }

    @Test
    void timeoutIsBoundedAndSanitized(CapturedOutput output) {
        server.expect(ExpectedCount.times(2), requestTo("https://api.pandascore.co/matches/1"))
                .andRespond(withException(new SocketTimeoutException("request Authorization=" + TOKEN)));
        var error = failure();
        assertThat(error.getReason()).isEqualTo(Reason.TIMEOUT);
        assertThat(error.getRetryAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(error.getMessage()).doesNotContain(TOKEN);
        assertThat(error.getCause()).isNull();
        assertThat(output.getAll()).doesNotContain(TOKEN);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void rejectedCredentialOrPlanDoesNotRetry(int status) {
        server.expect(requestTo("https://api.pandascore.co/matches/1")).andRespond(withStatus(HttpStatus.valueOf(status)));
        assertThat(failure().getReason()).isEqualTo(Reason.AUTHENTICATION);
        assertThat(failure().getReason()).isEqualTo(Reason.AUTHENTICATION);
        server.verify();
    }

    @Test
    void missingMatchIsReportedWithoutProviderWideCooldown() {
        server.expect(requestTo("https://api.pandascore.co/matches/1")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(failure().getReason()).isEqualTo(Reason.NOT_FOUND);
        assertThat(client.nextAllowedRequestAt()).isNull();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "invalid json", "{\"results\":[{\"team_id\":1001,\"score\":1.5}]}", "{\"id\":1.2}", "{\"id\":1} {\"id\":2}"})
    void malformedPayloadNeverCreatesInventedIntegerData(String payload) {
        server.expect(requestTo("https://api.pandascore.co/matches/1")).andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
        assertThat(failure().getReason()).isEqualTo(Reason.INVALID_RESPONSE);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[{\"id\":1},null]", "[{\"id\":1},123]", "[{\"id\":1},{\"id\":1.5}]", "{\"id\":1}"})
    void malformedListIsRejectedAtomicallyInsteadOfReturningAPartialFeed(String payload) {
        server.expect(request -> { }).andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
        var error = catchThrowableOfType(() -> client.list("/csgo/teams", Map.of(), PandaScoreDtos.Team.class), SportsProviderException.class);
        assertThat(error.getReason()).isEqualTo(Reason.INVALID_RESPONSE);
        assertThat(error.getRetryAt()).isEqualTo(NOW.plusMillis(properties.getFailureBackoffMs()));
        server.verify();
    }

    @Test
    void invalidPayloadBacksOffWithoutSpendingAnotherRequest() {
        server.expect(requestTo("https://api.pandascore.co/matches/1")).andRespond(withSuccess("invalid",MediaType.APPLICATION_JSON));
        assertThat(failure().getReason()).isEqualTo(Reason.INVALID_RESPONSE);
        assertThat(client.lastHttpStatus()).isEqualTo(200);
        assertThat(failure().getReason()).isEqualTo(Reason.INVALID_RESPONSE);
        server.verify();
    }

    @Test
    void malformedRateHeaderDoesNotDiscardValidPayload() {
        server.expect(requestTo("https://api.pandascore.co/matches/1"))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON).header("X-Rate-Limit-Remaining", "unknown"));
        assertThat(client.detail("/matches/1", PandaScoreDtos.Match.class).id()).isEqualTo(1L);
        assertThat(client.remainingRequests()).isNull();
        assertThat(client.nextAllowedRequestAt()).isNull();
        server.verify();
    }

    private SportsProviderException failure() {
        return catchThrowableOfType(() -> client.detail("/matches/1", PandaScoreDtos.Match.class), SportsProviderException.class);
    }

    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
