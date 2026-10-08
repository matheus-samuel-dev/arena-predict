package com.bolao.copa.arena.service.provider;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Bounded blocking transport. Rate protection is shared by every endpoint of this provider. */
public class BoundedSportsHttpClient {
    private static final Logger log = LoggerFactory.getLogger(BoundedSportsHttpClient.class);
    private static final int MAX_RESPONSE_BYTES = 8_000_000;
    private final SportsHttpSettings properties;
    private final ObjectMapper json;
    private final RestClient http;
    private final Clock clock;
    private final ArrayDeque<Instant> requestTimes = new ArrayDeque<>();
    private final ArrayDeque<Instant> dailyTimes = new ArrayDeque<>();
    private final ArrayDeque<Instant> minuteTimes = new ArrayDeque<>();
    private volatile Instant retryAt = Instant.EPOCH;
    private Reason cooldownReason = Reason.RATE_LIMITED;
    private volatile Long remainingRequests;
    private volatile Integer lastHttpStatus;

    public BoundedSportsHttpClient(SportsHttpSettings properties, ObjectMapper json) {
        this(properties, json, buildHttp(properties), Clock.systemUTC());
    }

    protected BoundedSportsHttpClient(SportsHttpSettings properties, ObjectMapper json, RestClient http, Clock clock) {
        this.properties = properties;
        // Scores must be integral. Jackson's default float-to-integer coercion would
        // silently turn a malformed provider score (e.g. 1.5) into a valid result.
        this.json = json.copy().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.http = http;
        this.clock = clock;
    }

    private static RestClient buildHttp(SportsHttpSettings properties) {
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
    public Instant nextAllowedRequestAt() {
        return clock.instant().isBefore(retryAt) ? retryAt : null;
    }

    /** Last quota reported by the provider; null means no valid header was received. */
    public Long remainingRequests() { return remainingRequests; }
    public Integer lastHttpStatus() { return lastHttpStatus; }

    public <T> List<T> list(String path, Map<String, String> query, Class<T> itemType) {
        List<T> items = new ArrayList<>();
        for (int page = 1; page <= properties.getMaxPages(); page++) {
            var params = new java.util.LinkedHashMap<>(query);
            params.put("per_page", Integer.toString(properties.getPageSize()));
            params.put("page", Integer.toString(page));
            JsonNode payload = payload(path, params);
            if (!payload.isArray()) throw invalidResponse();
            for (JsonNode item : payload) {
                if (!item.isObject()) throw invalidResponse();
                try { items.add(json.treeToValue(item, itemType)); }
                catch (IOException | IllegalArgumentException ex) { throw invalidResponse(); }
            }
            if (payload.size() < properties.getPageSize()) break;
            if (page == properties.getMaxPages()) {
                log.warn("[SPORTS_SYNC] Sports provider pagination cap reached path={} maxPages={}; narrow the sync window or raise the configured cap", path, page);
            }
        }
        return List.copyOf(items);
    }

    public <T> T detail(String path, Class<T> type) {
        JsonNode payload = payload(path, Map.of());
        if (!payload.isObject()) throw invalidResponse();
        try { return json.treeToValue(payload, type); }
        catch (IOException | IllegalArgumentException ex) { throw invalidResponse(); }
    }

    public synchronized JsonNode payload(String path, Map<String, String> query) {
        if (!configured()) throw new SportsProviderException(Reason.NOT_CONFIGURED, "Sports provider token is not configured", null);
        for (int attempt = 0; attempt <= properties.getMaxRetries(); attempt++) {
            reserveRequest();
            try {
                var request = http.method(properties.authentication()==SportsHttpSettings.Authentication.FORM ? org.springframework.http.HttpMethod.POST : org.springframework.http.HttpMethod.GET).uri(builder -> {
                    builder.path(path);
                    if(properties.authentication()!=SportsHttpSettings.Authentication.FORM) query.forEach(builder::queryParam);
                    return builder.build();
                }).accept(MediaType.APPLICATION_JSON).headers(headers -> { if(properties.authentication()==SportsHttpSettings.Authentication.BEARER) headers.setBearerAuth(properties.getApiToken().strip()); else if(properties.authentication()==SportsHttpSettings.Authentication.HEADER) headers.set(properties.credentialName(),properties.getApiToken().strip()); })
                        ;
                if(properties.authentication()==SportsHttpSettings.Authentication.FORM) {
                    var form=new org.springframework.util.LinkedMultiValueMap<String,String>(); query.forEach(form::add); form.add(properties.credentialName(),properties.getApiToken().strip());
                    request.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form);
                }
                return request.exchange((httpRequest, response) -> {
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
                    throw new SportsProviderException(Reason.RATE_LIMITED, "Sports provider rate limit reached", retryAt);
                }
                if (ex.status == 401 || ex.status == 403) {
                    setCooldown(clock.instant().plusMillis(properties.getRateLimitBackoffMs()), Reason.AUTHENTICATION);
                    throw new SportsProviderException(Reason.AUTHENTICATION, "Sports provider rejected the token or the requested plan capability", retryAt);
                }
                if (ex.status == 404) throw new SportsProviderException(Reason.NOT_FOUND, "Sports provider match was not found", null);
                if (ex.status < 500) throw invalidResponse();
                if (ex.headers.getFirst(HttpHeaders.RETRY_AFTER) != null) {
                    // A maintenance response may explicitly ask clients to wait. Do not
                    // spend the normal immediate retry on a request that cannot succeed yet.
                    setCooldown(retryAfter(ex.headers.getFirst(HttpHeaders.RETRY_AFTER)), Reason.UNAVAILABLE);
                    throw new SportsProviderException(Reason.UNAVAILABLE,
                            "Sports provider is temporarily unavailable; respecting the provider retry window", retryAt);
                }
                if (attempt == properties.getMaxRetries()) throw unavailable(Reason.UNAVAILABLE);
                log.warn("[SPORTS_SYNC] Sports provider temporary HTTP failure status={} retry={}", ex.status, attempt + 1);
            } catch (ResourceAccessException ex) {
                Reason reason = isTimeout(ex) ? Reason.TIMEOUT : Reason.UNAVAILABLE;
                if (attempt == properties.getMaxRetries()) throw unavailable(reason);
                log.warn("[SPORTS_SYNC] Sports provider transport failure reason={} retry={}", reason, attempt + 1);
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
        if (now.isBefore(retryAt)) throw new SportsProviderException(cooldownReason, "Sports provider requests are paused until the retry window", retryAt);
        Instant cutoff = now.minus(Duration.ofHours(1));
        while(!minuteTimes.isEmpty()&&!minuteTimes.peekFirst().isAfter(now.minusSeconds(60))) minuteTimes.removeFirst();
        if(minuteTimes.size()>=properties.getMaxRequestsPerMinute()) {
            setCooldown(minuteTimes.peekFirst().plusSeconds(60),Reason.RATE_LIMITED);
            throw new SportsProviderException(Reason.RATE_LIMITED,"Provider minute budget reached",retryAt);
        }
        while (!requestTimes.isEmpty() && !requestTimes.peekFirst().isAfter(cutoff)) requestTimes.removeFirst();
        if (requestTimes.size() >= properties.getMaxRequestsPerHour()) {
            setCooldown(requestTimes.peekFirst().plus(Duration.ofHours(1)), Reason.RATE_LIMITED);
            throw new SportsProviderException(Reason.RATE_LIMITED, "Sports provider local hourly request budget reached", retryAt);
        }
        Instant dayCutoff=now.minus(Duration.ofDays(1));
        while(!dailyTimes.isEmpty() && !dailyTimes.peekFirst().isAfter(dayCutoff)) dailyTimes.removeFirst();
        if(dailyTimes.size()>=properties.getMaxRequestsPerDay()) {
            setCooldown(dailyTimes.peekFirst().plus(Duration.ofDays(1)),Reason.RATE_LIMITED);
            throw new SportsProviderException(Reason.RATE_LIMITED,"Provider daily budget reached",retryAt);
        }
        requestTimes.addLast(now); dailyTimes.addLast(now);minuteTimes.addLast(now);
    }

    private void accountHeaders(HttpHeaders headers) {
        if(properties.authentication()==SportsHttpSettings.Authentication.HEADER) {
            try { if(Long.parseLong(headers.getFirst("X-RateLimit-Remaining"))<=0) setCooldown(clock.instant().plusSeconds(60),Reason.RATE_LIMITED); }
            catch(NumberFormatException ignored) { }
        }
        String remaining = headers.getFirst(properties.remainingHeader());
        if (remaining == null) return;
        try {
            long parsed = Long.parseLong(remaining);
            if (parsed < 0) return;
            remainingRequests = parsed;
            if (parsed <= properties.getRemainingReserve()) {
                setCooldown(retryAfter(headers.getFirst(HttpHeaders.RETRY_AFTER)), Reason.RATE_LIMITED);
                log.warn("[SPORTS_SYNC] Sports provider remaining quota reserve reached; requests paused until {}", retryAt);
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
        // Sports provider documents remaining requests, but not a guaranteed reset header.
        return now.plusMillis(properties.getRateLimitBackoffMs());
    }

    private void setCooldown(Instant until, Reason reason) {
        if (until.isAfter(retryAt)) { retryAt = until; cooldownReason = reason; }
    }

    private SportsProviderException unavailable(Reason reason) {
        setCooldown(clock.instant().plusMillis(properties.getFailureBackoffMs()), reason);
        return new SportsProviderException(reason, "Sports provider is temporarily unavailable; persisted sports data remains available", retryAt);
    }

    private synchronized SportsProviderException invalidResponse() {
        setCooldown(clock.instant().plusMillis(properties.getFailureBackoffMs()),Reason.INVALID_RESPONSE);
        return new SportsProviderException(Reason.INVALID_RESPONSE, "Sports provider returned an invalid or unsupported response", retryAt);
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
