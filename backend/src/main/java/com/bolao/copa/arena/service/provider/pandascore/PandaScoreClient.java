package com.bolao.copa.arena.service.provider.pandascore;

import com.bolao.copa.arena.service.provider.SportsProviderException;
import com.bolao.copa.arena.service.provider.SportsProviderException.Reason;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Bounded blocking transport. Rate protection is shared by every endpoint of this provider. */
@Component
public class PandaScoreClient {
    private static final Logger log = LoggerFactory.getLogger(PandaScoreClient.class);
    private static final int MAX_RESPONSE_BYTES = 8_000_000;
    private final PandaScoreProperties properties;
    private final ObjectMapper json;
    private final RestClient http;
    private final Clock clock;
    private final ArrayDeque<Instant> requestTimes = new ArrayDeque<>();
    private Instant retryAt = Instant.EPOCH;
    private Reason cooldownReason = Reason.RATE_LIMITED;
    private Long remainingRequests;
    private Integer lastHttpStatus;

    @Autowired
    public PandaScoreClient(PandaScoreProperties properties, ObjectMapper json) {
        this(properties, json, buildHttp(properties), Clock.systemUTC());
    }

    PandaScoreClient(PandaScoreProperties properties, ObjectMapper json, RestClient http, Clock clock) {
        this.properties = properties;
        // Scores must be integral. Jackson's default float-to-integer coercion would
        // silently turn a malformed provider score (e.g. 1.5) into a valid result.
        this.json = json.copy().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.http = http;
        this.clock = clock;
    }

    private static RestClient buildHttp(PandaScoreProperties properties) {
        // The socket read timeout also protects body reads after response headers.
        // A timeout on JDK BodyHandlers.ofInputStream only bounds receipt of headers.
        var factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                // Never forward the Bearer credential to a redirect destination.
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
    }

    public boolean configured() { return properties.getApiToken() != null && !properties.getApiToken().isBlank(); }

    /** Safe operational metadata; never exposes credentials, URLs or response bodies. */
    public synchronized Instant nextAllowedRequestAt() {
        return clock.instant().isBefore(retryAt) ? retryAt : null;
    }

    /** Last quota reported by the provider; null means no valid header was received. */
    public synchronized Long remainingRequests() { return remainingRequests; }
    public synchronized Integer lastHttpStatus() { return lastHttpStatus; }

    public <T> List<T> list(String path, Map<String, String> query, Class<T> itemType) {
        List<T> items = new ArrayList<>();
        for (int page = 1; page <= properties.getMaxPages(); page++) {
            var params = new java.util.LinkedHashMap<>(query);
            params.put("per_page", Integer.toString(properties.getPageSize()));
            params.put("page", Integer.toString(page));
            JsonNode payload = get(path, params);
            if (!payload.isArray()) throw invalidResponse();
            for (JsonNode item : payload) {
                if (!item.isObject()) throw invalidResponse();
                try { items.add(json.treeToValue(item, itemType)); }
                catch (IOException | IllegalArgumentException ex) { throw invalidResponse(); }
            }
            if (payload.size() < properties.getPageSize()) break;
            if (page == properties.getMaxPages()) {
                log.warn("[SPORTS_SYNC] PandaScore pagination cap reached path={} maxPages={}; narrow the sync window or raise the configured cap", path, page);
            }
        }
        return List.copyOf(items);
    }

    public <T> T detail(String path, Class<T> type) {
        JsonNode payload = get(path, Map.of());
        if (!payload.isObject()) throw invalidResponse();
        try { return json.treeToValue(payload, type); }
        catch (IOException | IllegalArgumentException ex) { throw invalidResponse(); }
    }

    private synchronized JsonNode get(String path, Map<String, String> query) {
        if (!configured()) throw new SportsProviderException(Reason.NOT_CONFIGURED, "PandaScore token is not configured", null);
        for (int attempt = 0; attempt <= properties.getMaxRetries(); attempt++) {
            reserveRequest();
            try {
                return http.get().uri(builder -> {
                    builder.path(path);
                    query.forEach(builder::queryParam);
                    return builder.build();
                }).accept(MediaType.APPLICATION_JSON).headers(headers -> headers.setBearerAuth(properties.getApiToken().strip()))
                        .exchange((request, response) -> {
                            int status = response.getStatusCode().value();
                            lastHttpStatus = status;
                            accountHeaders(response.getHeaders());
                            if (status < 200 || status >= 300) throw new HttpFailure(status, response.getHeaders());
                            byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                            if (body.length > MAX_RESPONSE_BYTES) throw invalidResponse();
                            JsonNode payload;
                            try { payload = json.readTree(body); }
                            catch (IOException ex) { throw invalidResponse(); }
                            if (payload == null || payload.isNull()) throw invalidResponse();
                            return payload;
                        });
            } catch (HttpFailure ex) {
                if (ex.status == 429) {
                    Instant next = retryAfter(ex.headers.getFirst(HttpHeaders.RETRY_AFTER));
                    setCooldown(next, Reason.RATE_LIMITED);
                    throw new SportsProviderException(Reason.RATE_LIMITED, "PandaScore rate limit reached", retryAt);
                }
                if (ex.status == 401 || ex.status == 403) {
                    setCooldown(clock.instant().plusMillis(properties.getRateLimitBackoffMs()), Reason.AUTHENTICATION);
                    throw new SportsProviderException(Reason.AUTHENTICATION, "PandaScore rejected the token or the requested plan capability", retryAt);
                }
                if (ex.status == 404) throw new SportsProviderException(Reason.NOT_FOUND, "PandaScore match was not found", null);
                if (ex.status < 500) throw invalidResponse();
                if (ex.headers.getFirst(HttpHeaders.RETRY_AFTER) != null) {
                    // A maintenance response may explicitly ask clients to wait. Do not
                    // spend the normal immediate retry on a request that cannot succeed yet.
                    setCooldown(retryAfter(ex.headers.getFirst(HttpHeaders.RETRY_AFTER)), Reason.UNAVAILABLE);
                    throw new SportsProviderException(Reason.UNAVAILABLE,
                            "PandaScore is temporarily unavailable; respecting the provider retry window", retryAt);
                }
                if (attempt == properties.getMaxRetries()) throw unavailable(Reason.UNAVAILABLE);
                log.warn("[SPORTS_SYNC] PandaScore temporary HTTP failure status={} retry={}", ex.status, attempt + 1);
            } catch (ResourceAccessException ex) {
                Reason reason = isTimeout(ex) ? Reason.TIMEOUT : Reason.UNAVAILABLE;
                if (attempt == properties.getMaxRetries()) throw unavailable(reason);
                log.warn("[SPORTS_SYNC] PandaScore transport failure reason={} retry={}", reason, attempt + 1);
            } catch (RestClientException ex) {
                throw invalidResponse();
            }
            try { Thread.sleep((long) properties.getRetryBackoffMs() * (1L << attempt)); }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw unavailable(Reason.UNAVAILABLE);
            }
        }
        throw unavailable(Reason.UNAVAILABLE);
    }

    private void reserveRequest() {
        Instant now = clock.instant();
        if (now.isBefore(retryAt)) throw new SportsProviderException(cooldownReason, "PandaScore requests are paused until the retry window", retryAt);
        Instant cutoff = now.minus(Duration.ofHours(1));
        while (!requestTimes.isEmpty() && !requestTimes.peekFirst().isAfter(cutoff)) requestTimes.removeFirst();
        if (requestTimes.size() >= properties.getMaxRequestsPerHour()) {
            setCooldown(requestTimes.peekFirst().plus(Duration.ofHours(1)), Reason.RATE_LIMITED);
            throw new SportsProviderException(Reason.RATE_LIMITED, "PandaScore local hourly request budget reached", retryAt);
        }
        requestTimes.addLast(now);
    }

    private void accountHeaders(HttpHeaders headers) {
        String remaining = headers.getFirst("X-Rate-Limit-Remaining");
        if (remaining == null) return;
        try {
            long parsed = Long.parseLong(remaining);
            if (parsed < 0) return;
            remainingRequests = parsed;
            if (parsed <= properties.getRemainingReserve()) {
                setCooldown(retryAfter(headers.getFirst(HttpHeaders.RETRY_AFTER)), Reason.RATE_LIMITED);
                log.warn("[SPORTS_SYNC] PandaScore remaining quota reserve reached; requests paused until {}", retryAt);
            }
        } catch (NumberFormatException ignored) { /* Absent/malformed optional headers do not break data delivery. */ }
    }

    private Instant retryAfter(String value) {
        Instant now = clock.instant();
        if (value != null) {
            try { return now.plusSeconds(Math.max(1, Long.parseLong(value))); }
            catch (NumberFormatException | java.time.DateTimeException ignored) { }
            try {
                Instant date = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return date.isAfter(now) ? date : now.plusSeconds(1);
            } catch (java.time.DateTimeException ignored) { }
        }
        // PandaScore documents remaining requests, but not a guaranteed reset header.
        return now.plusMillis(properties.getRateLimitBackoffMs());
    }

    private void setCooldown(Instant until, Reason reason) {
        if (until.isAfter(retryAt)) { retryAt = until; cooldownReason = reason; }
    }

    private SportsProviderException unavailable(Reason reason) {
        setCooldown(clock.instant().plusMillis(properties.getFailureBackoffMs()), reason);
        return new SportsProviderException(reason, "PandaScore is temporarily unavailable; persisted sports data remains available", retryAt);
    }

    private synchronized SportsProviderException invalidResponse() {
        setCooldown(clock.instant().plusMillis(properties.getFailureBackoffMs()),Reason.INVALID_RESPONSE);
        return new SportsProviderException(Reason.INVALID_RESPONSE, "PandaScore returned an invalid or unsupported response", retryAt);
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException
                    || cause instanceof java.util.concurrent.TimeoutException) return true;
        return false;
    }

    private static final class HttpFailure extends RuntimeException {
        final int status;
        final HttpHeaders headers;
        HttpFailure(int status, HttpHeaders headers) { this.status = status; this.headers = headers; }
    }
}
