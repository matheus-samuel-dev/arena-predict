package com.bolao.copa.arena.service.provider;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** Shared transport only. Each adapter owns its sport's schema and status contract. */
public abstract class DocumentedSportsProvider implements SportsDataProvider {
    protected final ProviderConfig config;
    protected final BoundedSportsHttpClient client;
    protected final Clock clock;
    private final String id, name, sport;
    private final Map<String, Cached> cache = new LinkedHashMap<>();
    protected DocumentedSportsProvider(String id, String name, String sport, ProviderConfig config, ObjectMapper json) {
        this(id,name,sport,config,new BoundedSportsHttpClient(config,json),Clock.systemUTC());
    }
    protected DocumentedSportsProvider(String id,String name,String sport,ProviderConfig config,BoundedSportsHttpClient client,Clock clock) {
        this.id=id; this.name=name; this.sport=sport; this.config=config; this.client=client; this.clock=clock;
    }
    public String providerId() { return id; }
    public String providerName() { return name; }
    public boolean demo() { return false; }
    public boolean available() { return client.configured(); }
    public boolean enabled() { return config.enabled(); }
    public long pollingIntervalMs() { return Math.max(60000,config.intervalMs()); }
    public String credentialVariable() { return config.credentialVariable(); }
    public List<String> supportedSports() { return List.of(sport); }
    public Instant nextAllowedRequestAt() { return client.nextAllowedRequestAt(); }
    public Long remainingRequests() { return client.remainingRequests(); }
    public Integer lastHttpStatus() { return client.lastHttpStatus(); }

    protected synchronized JsonNode fetch(String path,Map<String,String> query,String resultField) {
        String key=path+new TreeMap<>(query);
        Cached saved=cache.get(key);
        if(saved!=null && clock.instant().isBefore(saved.expiresAt())) return saved.data();
        JsonNode body=client.payload(path,query);
        JsonNode errors=body.path("errors");
        if ((errors.isContainerNode() && !errors.isEmpty()) || (body.has("success") && body.path("success").asInt()!=1)) {
            // Never propagate a remote error body: it may echo credentials.
            var reason=errors.has("requests")?SportsProviderException.Reason.RATE_LIMITED:SportsProviderException.Reason.INVALID_RESPONSE;
            throw new SportsProviderException(reason,"Provider rejected the request",clock.instant().plusMillis(
                    reason==SportsProviderException.Reason.RATE_LIMITED?config.getRateLimitBackoffMs():config.getFailureBackoffMs()));
        }
        JsonNode rows=body.path(resultField);
        if(!rows.isArray() || rows.size()>5000) throw invalid();
        if(body.path("paging").path("total").asInt(1)>1) throw invalid(); // Never certify a truncated feed as complete.
        cache.put(key,new Cached(rows,clock.instant().plusMillis(Math.min(pollingIntervalMs(),60000))));
        while(cache.size()>64) cache.remove(cache.keySet().iterator().next());
        return rows;
    }
    protected List<SportsMatch> mapped(JsonNode rows) {
        List<SportsMatch> result=new ArrayList<>();
        for(JsonNode row:rows) result.add(map(row));
        return List.copyOf(result);
    }
    public abstract SportsMatch map(JsonNode source);
    protected static SportsProviderException invalid() {
        return new SportsProviderException(SportsProviderException.Reason.INVALID_RESPONSE,"Invalid sports response",Instant.now().plusSeconds(60));
    }
    protected static String text(JsonNode value) { return value.isMissingNode()||value.isNull()?null:value.asText(); }
    protected static String required(JsonNode value) { String s=text(value); if(s==null||s.isBlank()) throw invalid(); return s; }
    protected static Integer number(JsonNode value) {
        if(value.isNull()||value.isMissingNode()||"".equals(value.asText())||"-".equals(value.asText())) return null;
        if(!value.asText().matches("[0-9]{1,6}")) throw invalid();
        return Integer.valueOf(value.asText());
    }
    protected static Instant instant(JsonNode value) {
        String s=text(value); if(s==null) return null;
        try { return OffsetDateTime.parse(s).toInstant(); } catch(DateTimeException ex) { throw invalid(); }
    }
    protected static SportsTeam team(JsonNode value) {
        return new SportsTeam(required(value.path("id")),required(value.path("name")),null,logo(text(value.path("logo"))));
    }
    protected static SportsChampionship league(JsonNode value) {
        return new SportsChampionship(required(value.path("id")),required(value.path("name")),null,text(value.path("season")),
                logo(text(value.path("logo"))),text(value.path("name")),null,null,null);
    }
    protected static String logo(String value) {
        if(value==null||value.length()>2048) return null;
        try {var uri=java.net.URI.create(value);return "https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null?value:null;}
        catch(IllegalArgumentException ex) {return null;}
    }
    protected static String winner(SportsTeam home,SportsTeam away,Integer hs,Integer as) {
        return hs==null||as==null||hs.equals(as)?null:hs>as?home.externalId():away.externalId();
    }
    protected static void pair(Map<String,String> data,String metric,Integer home,Integer away) {
        if(home!=null && away!=null) { data.put(metric+"Home",home.toString()); data.put(metric+"Away",away.toString()); }
    }
    protected static List<SportsMatch> filter(List<SportsMatch> matches,EventStatus status) {
        return matches.stream().filter(m->m.status()==status).toList();
    }
    protected static String date(Instant value) { return value.atZone(ZoneOffset.UTC).toLocalDate().toString(); }
    private record Cached(JsonNode data,Instant expiresAt) { }
}
