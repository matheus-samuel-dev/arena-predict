package com.bolao.copa.arena.service.provider.pandascore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.bolao.copa.arena.service.provider.SportsProviderException;
import com.bolao.copa.arena.service.provider.SportsProviderException.Reason;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises the production transport against loopback, never the external API. */
class PandaScoreTransportTest {
    @Test
    void socketReadTimeoutCoversStalledBodyAfterHeaders() throws Exception {
        var releaseBody = new CountDownLatch(1);
        var headersSent = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(executor);
            server.createContext("/matches/1", exchange -> {
                try (exchange) {
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, 16);
                    exchange.getResponseBody().write('{');
                    exchange.getResponseBody().flush();
                    headersSent.countDown();
                    try { releaseBody.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                }
            });
            server.start();
            try {
                PandaScoreClient client = client(server);
                var error = assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                        catchThrowableOfType(() -> client.detail("/matches/1", PandaScoreDtos.Match.class), SportsProviderException.class));
                assertThat(headersSent.getCount()).isZero();
                assertThat(error).isNotNull();
                assertThat(error.getReason()).isEqualTo(Reason.TIMEOUT);
                assertThat(client.nextAllowedRequestAt()).isNotNull();
            } finally {
                releaseBody.countDown();
                server.stop(0);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void redirectsAreNotFollowedWithTheBearerCredential(int redirectStatus) throws Exception {
        var redirected = new AtomicBoolean();
        var authorizedInitialRequest = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/matches/1", exchange -> {
            try (exchange) {
                authorizedInitialRequest.set("Bearer test-loopback-only".equals(exchange.getRequestHeaders().getFirst("Authorization")));
                exchange.getResponseHeaders().add("Location", "/redirected");
                exchange.sendResponseHeaders(redirectStatus, -1);
            }
        });
        server.createContext("/redirected", exchange -> {
            try (exchange) {
                redirected.set(true);
                byte[] body = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            var error = catchThrowableOfType(() -> client(server).detail("/matches/1", PandaScoreDtos.Match.class), SportsProviderException.class);
            assertThat(error).isNotNull();
            assertThat(error.getReason()).isEqualTo(Reason.INVALID_RESPONSE);
            assertThat(authorizedInitialRequest).isTrue();
            assertThat(redirected).isFalse();
        } finally {
            server.stop(0);
        }
    }

    private PandaScoreClient client(HttpServer server) {
        var properties = new PandaScoreProperties();
        properties.setApiToken("test-loopback-only");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setConnectTimeoutMs(1000);
        properties.setReadTimeoutMs(100);
        properties.setMaxRetries(0);
        return new PandaScoreClient(properties, new ObjectMapper().findAndRegisterModules());
    }
}
