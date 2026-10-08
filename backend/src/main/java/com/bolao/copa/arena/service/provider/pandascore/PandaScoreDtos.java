package com.bolao.copa.arena.service.provider.pandascore;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;

/** REST fixtures fields only; series results are distinct from paid live frames/rounds. */
public final class PandaScoreDtos {
    private PandaScoreDtos() { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Match(Long id, String name, Instant scheduledAt, Instant beginAt, Instant endAt,
            String status, String matchType, Integer numberOfGames, Long winnerId, Team winner,
            Boolean forfeit, Boolean draw, List<Opponent> opponents, List<Result> results,
            Tournament tournament, League league, Series serie, Videogame videogame, List<Game> games) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Game(Long id, Integer position, String status, Boolean finished) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Opponent(String type, Team opponent) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Team(Long id, String name, String acronym, String imageUrl) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Result(Long teamId, Integer score) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record League(Long id, String name, String imageUrl) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Series(Long id, String name, String fullName, String season, Integer year) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Tournament(Long id, String name, String slug, Instant beginAt, Instant endAt,
            League league, Series serie) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Videogame(Long id, String name, String slug) { }
}
