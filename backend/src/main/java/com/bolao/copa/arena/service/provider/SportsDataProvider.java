package com.bolao.copa.arena.service.provider;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface SportsDataProvider {
    String providerName();
    boolean demo();
    default String providerId() { return providerName(); }
    default boolean available() { return true; }
    default Instant nextAllowedRequestAt() { return null; }
    default Long remainingRequests() { return null; }
    default Integer lastHttpStatus() { return null; }
    default List<String> supportedSports() { return List.of(); }
    default boolean enabled() { return true; }
    default int priority() { return 100; }
    default long pollingIntervalMs() { return 120000; }
    default String credentialVariable() { return "PANDASCORE_API_TOKEN"; }
    default java.util.Set<ProviderCapability> capabilities(String sport) {
        return java.util.Set.of(ProviderCapability.EVENTS,ProviderCapability.LIVE_STATUS,ProviderCapability.PARTICIPANTS,
                ProviderCapability.CHAMPIONSHIPS,ProviderCapability.RESULTS);
    }
    default java.util.Set<String> resultMetrics(String sport) { return java.util.Set.of("score"); }
    default List<LiveEventUpdate> liveUpdates() { return List.of(); }
    default List<SportsMatch> upcomingMatches(Instant from, Instant to) { return List.of(); }
    default List<SportsMatch> runningMatches() { return List.of(); }
    default List<SportsMatch> finishedMatches(Instant since) { return List.of(); }
    default Optional<SportsMatch> matchDetails(String externalId) { return Optional.empty(); }
    default List<SportsMatch> matchDetails(List<String> externalIds) {
        return externalIds.stream().map(this::matchDetails).flatMap(Optional::stream).toList();
    }
    default List<SportsTeam> teams() { return List.of(); }
    default List<SportsChampionship> championships() { return List.of(); }

    record LiveEventUpdate(String externalKey, Integer homeScore, Integer awayScore,
                           String clock, String period, String structuredData) { }
}
