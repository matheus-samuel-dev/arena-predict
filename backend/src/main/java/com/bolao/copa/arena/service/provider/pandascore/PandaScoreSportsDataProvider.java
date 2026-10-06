package com.bolao.copa.arena.service.provider.pandascore;

import com.bolao.copa.arena.service.provider.EsportsDataProvider;
import com.bolao.copa.arena.service.provider.SportsChampionship;
import com.bolao.copa.arena.service.provider.SportsMatch;
import com.bolao.copa.arena.service.provider.SportsProviderException;
import com.bolao.copa.arena.service.provider.SportsTeam;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PandaScoreSportsDataProvider implements EsportsDataProvider {
    private static final Logger log = LoggerFactory.getLogger(PandaScoreSportsDataProvider.class);
    private final PandaScoreClient client;
    private final PandaScoreMapper mapper;
    private final PandaScoreProperties properties;
    private final Clock clock;
    private boolean synchronizationEnabled=true;
    private List<SportsTeam> cachedTeams;
    private List<SportsChampionship> cachedChampionships;
    private Instant teamsExpiresAt = Instant.EPOCH;
    private Instant championshipsExpiresAt = Instant.EPOCH;

    public PandaScoreSportsDataProvider(PandaScoreClient client, PandaScoreMapper mapper, PandaScoreProperties properties) {
        this(client, mapper, properties, Clock.systemUTC());
    }
    @Autowired
    public PandaScoreSportsDataProvider(PandaScoreClient client,PandaScoreMapper mapper,PandaScoreProperties properties,
            com.bolao.copa.arena.config.SportsSyncProperties synchronization) {
        this(client,mapper,properties,Clock.systemUTC()); synchronizationEnabled=synchronization.enabled();
    }

    PandaScoreSportsDataProvider(PandaScoreClient client, PandaScoreMapper mapper, PandaScoreProperties properties, Clock clock) {
        this.client = client; this.mapper = mapper; this.properties = properties;
        this.clock = clock;
        if (!client.configured()) log.info("[SPORTS_SYNC] PandaScore synchronization unavailable: PANDASCORE_API_TOKEN is not configured");
    }

    @Override public String providerName() { return "PandaScore"; }
    @Override public String providerId() { return "PANDASCORE"; }
    @Override public boolean demo() { return false; }
    @Override public boolean available() { return client.configured(); }
    @Override public boolean enabled() { return synchronizationEnabled; }
    @Override public java.util.Set<com.bolao.copa.arena.service.provider.ProviderCapability> capabilities(String sport) {
        var values=new java.util.HashSet<>(EsportsDataProvider.super.capabilities(sport));
        if(properties.isLiveScoresEnabled()) values.add(com.bolao.copa.arena.service.provider.ProviderCapability.LIVE_SCORE);
        return java.util.Set.copyOf(values);
    }
    @Override public Instant nextAllowedRequestAt() { return client.nextAllowedRequestAt(); }
    @Override public Long remainingRequests() { return client.remainingRequests(); }
    @Override public Integer lastHttpStatus() { return client.lastHttpStatus(); }
    @Override public List<String> supportedSports() {
        return properties.getVideogames().stream().map(PandaScoreGame::fromPath).map(PandaScoreGame::sportCode).distinct().toList();
    }

    @Override public List<SportsMatch> upcomingMatches(Instant from, Instant to) {
        return gameMatches("/matches/upcoming", Map.of("range[scheduled_at]", from + "," + to, "sort", "scheduled_at,id"));
    }

    @Override public List<SportsMatch> runningMatches() {
        return gameMatches("/matches/running", Map.of("sort", "id"));
    }

    @Override public List<SportsMatch> finishedMatches(Instant since) {
        return gameMatches("/matches/past", Map.of("range[end_at]", since + "," + clock.instant(), "sort", "-end_at,id"));
    }

    @Override public Optional<SportsMatch> matchDetails(String externalId) {
        validateId(externalId);
        try {
            // The generic detail endpoint is included in Fixtures; CS-specific details are paid.
            return mapped(client.detail("/matches/" + externalId, PandaScoreDtos.Match.class));
        } catch (SportsProviderException ex) {
            if (ex.getReason() == SportsProviderException.Reason.NOT_FOUND) return Optional.empty();
            throw ex;
        }
    }

    @Override public List<SportsMatch> matchDetails(List<String> externalIds) {
        List<String> ids = externalIds.stream().distinct().peek(PandaScoreSportsDataProvider::validateId).toList();
        List<SportsMatch> result = new ArrayList<>();
        for (int offset = 0; offset < ids.size(); offset += properties.getPageSize()) {
            var batch = ids.subList(offset, Math.min(offset + properties.getPageSize(), ids.size()));
            // Generic IDs are global; one batch covers all supported games without three lookups.
            result.addAll(matches("/matches", Map.of("filter[id]", String.join(",", batch), "sort", "id")));
        }
        return List.copyOf(result);
    }

    @Override public synchronized List<SportsTeam> teams() {
        if (cachedTeams != null && clock.instant().isBefore(teamsExpiresAt)) return cachedTeams;
        Map<String,SportsTeam> result=new LinkedHashMap<>();
        for(String game:properties.getVideogames().stream().distinct().toList())
            for(var source:client.list("/"+PandaScoreGame.fromPath(game).path()+"/teams",Map.of("sort","id"),PandaScoreDtos.Team.class)) {
                var team=mapper.team(source); if(team!=null) result.putIfAbsent(team.externalId(),team);
            }
        cachedTeams = List.copyOf(result.values());
        teamsExpiresAt = clock.instant().plusMillis(properties.getReferenceCacheTtlMs());
        return cachedTeams;
    }

    @Override public synchronized List<SportsChampionship> championships() {
        if (cachedChampionships != null && clock.instant().isBefore(championshipsExpiresAt)) return cachedChampionships;
        Map<String,SportsChampionship> result=new LinkedHashMap<>();
        for(String game:properties.getVideogames().stream().distinct().toList())
            for(var source:client.list("/"+PandaScoreGame.fromPath(game).path()+"/tournaments/upcoming",Map.of("sort","id"),PandaScoreDtos.Tournament.class)) {
                var championship=mapper.championship(source,null,null);
                if(championship!=null) result.putIfAbsent(championship.externalId(),championship);
            }
        cachedChampionships = List.copyOf(result.values());
        championshipsExpiresAt = clock.instant().plusMillis(properties.getReferenceCacheTtlMs());
        return cachedChampionships;
    }

    private List<SportsMatch> matches(String path, Map<String, String> query) {
        Map<String, SportsMatch> unique = new LinkedHashMap<>();
        for (var value : client.list(path, query, PandaScoreDtos.Match.class)) {
            mapped(value).filter(match -> supportedSports().contains(match.sportCode()))
                    .ifPresent(match -> unique.put(match.externalId(), match));
        }
        return List.copyOf(unique.values());
    }

    private Optional<SportsMatch> mapped(PandaScoreDtos.Match value) {
        if (value!=null && PandaScoreGame.from(value.videogame()).isPresent() && PandaScoreStatusMapper.map(value.status()).isEmpty())
            throw new SportsProviderException(SportsProviderException.Reason.INVALID_RESPONSE,
                    "PandaScore returned an unrecognized match status",clock.instant().plusMillis(properties.getFailureBackoffMs()));
        return mapper.match(value,properties.isLiveScoresEnabled());
    }

    private List<SportsMatch> gameMatches(String suffix, Map<String,String> query) {
        Map<String,SportsMatch> result = new LinkedHashMap<>();
        for (String game : properties.getVideogames().stream().distinct().toList())
            for (SportsMatch match : matches("/" + PandaScoreGame.fromPath(game).path() + suffix,query))
                result.put(match.externalId(),match);
        return List.copyOf(result.values());
    }

    private static void validateId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("A positive numeric external match ID is required");
    }
}
