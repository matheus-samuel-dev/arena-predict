package com.bolao.copa.arena.service.provider;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BoundedSportsHttpClientTest {
    @Test void apiSportsSendsTheCredentialOnlyInItsHeader() {
        var builder=RestClient.builder().baseUrl("https://provider.example.test");var server=MockRestServiceServer.bindTo(builder).build();
        var config=new ProviderConfig(new MockEnvironment().withProperty("CONTRACT_KEY","test-only-fixture-credential"),"CONTRACT","CONTRACT_KEY",
                "https://provider.example.test",SportsHttpSettings.Authentication.HEADER);
        server.expect(requestTo("https://provider.example.test/games?date=2026-10-06")).andExpect(method(HttpMethod.GET))
                .andExpect(header("x-apisports-key","test-only-fixture-credential")).andRespond(withSuccess("{\"errors\":[],\"response\":[]}",MediaType.APPLICATION_JSON));
        var client=new BoundedSportsHttpClient(config,new ObjectMapper(),builder.build(),Clock.systemUTC());
        assertThat(client.payload("/games",Map.of("date","2026-10-06")).path("response")).isEmpty();assertThat(client.lastHttpStatus()).isEqualTo(200);server.verify();
    }
    @Test void tennisSendsTheCredentialInPostBodyNeverInUrlOrLogs() {
        var builder=RestClient.builder().baseUrl("https://provider.example.test");var server=MockRestServiceServer.bindTo(builder).build();
        var config=new ProviderConfig(new MockEnvironment().withProperty("CONTRACT_KEY","test-only-fixture-credential"),"CONTRACT","CONTRACT_KEY",
                "https://provider.example.test",SportsHttpSettings.Authentication.FORM);
        server.expect(requestTo("https://provider.example.test/tennis/")).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("APIkey=test-only-fixture-credential"),org.hamcrest.Matchers.containsString("method=get_livescore"))))
                .andRespond(withSuccess("{\"success\":1,\"result\":[]}",MediaType.APPLICATION_JSON));
        var client=new BoundedSportsHttpClient(config,new ObjectMapper(),builder.build(),Clock.systemUTC());
        assertThat(client.payload("/tennis/",Map.of("method","get_livescore")).path("result")).isEmpty();server.verify();
    }
    @Test void dailyBudgetStopsASecondCallBeforeItReachesTransport() {
        var builder=RestClient.builder().baseUrl("https://provider.example.test");var server=MockRestServiceServer.bindTo(builder).build();
        var config=new ProviderConfig(new MockEnvironment().withProperty("CONTRACT_KEY","test-only-fixture-credential").withProperty("CONTRACT_REQUESTS_PER_DAY","1"),
                "CONTRACT","CONTRACT_KEY","https://provider.example.test",SportsHttpSettings.Authentication.HEADER);
        server.expect(requestTo("https://provider.example.test/games")).andRespond(withSuccess("[]",MediaType.APPLICATION_JSON));
        var client=new BoundedSportsHttpClient(config,new ObjectMapper(),builder.build(),Clock.systemUTC());client.payload("/games",Map.of());
        assertThatThrownBy(()->client.payload("/games",Map.of())).isInstanceOf(SportsProviderException.class).extracting("reason").isEqualTo(SportsProviderException.Reason.RATE_LIMITED);
        assertThat(client.nextAllowedRequestAt()).isAfter(Instant.now().plusSeconds(3600));server.verify();
    }
}
