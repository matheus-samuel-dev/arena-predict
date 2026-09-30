package com.bolao.copa.arena.service.provider.pandascore;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.service.provider.SportsChampionship;
import com.bolao.copa.arena.service.provider.SportsMatch;
import com.bolao.copa.arena.service.provider.SportsTeam;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PandaScoreMapper {
    private static final Logger log = LoggerFactory.getLogger(PandaScoreMapper.class);

    public Optional<SportsMatch> match(PandaScoreDtos.Match source, boolean liveScoresEnabled) {
        if (source == null || source.id() == null || source.id() <= 0) return Optional.empty();
        var status = PandaScoreStatusMapper.map(source.status()).orElse(null);
        if (status == null || !isCounterStrike(source.videogame())) {
            log.warn("[SPORTS_SYNC] Ignoring unsupported PandaScore match externalId={}", source.id());
            return Optional.empty();
        }
        List<PandaScoreDtos.Team> opponents = source.opponents() == null ? List.of() : source.opponents().stream()
                .filter(item -> item != null && "Team".equals(item.type()) && item.opponent() != null)
                .map(PandaScoreDtos.Opponent::opponent).toList();
        if (opponents.size() > 2) {
            log.warn("[SPORTS_SYNC] Ignoring ambiguous PandaScore opponents externalId={}", source.id());
            return Optional.empty();
        }
        SportsTeam home = opponents.isEmpty() ? null : team(opponents.getFirst());
        SportsTeam away = opponents.size() < 2 ? null : team(opponents.get(1));
        boolean acceptScore = status == EventStatus.FINISHED || (status == EventStatus.LIVE && liveScoresEnabled);
        Integer homeScore = acceptScore && home != null ? score(source.results(), home.externalId()) : null;
        Integer awayScore = acceptScore && away != null ? score(source.results(), away.externalId()) : null;
        Long winner = winnerId(source);
        Integer bestOf = "best_of".equals(source.matchType()) && source.numberOfGames() != null && List.of(1, 3, 5).contains(source.numberOfGames())
                ? source.numberOfGames() : null;
        return Optional.of(new SportsMatch(source.id().toString(), clean(source.name()), home, away,
                championship(source.tournament(), source.league(), source.serie()),
                source.scheduledAt() != null ? source.scheduledAt() : source.beginAt(), source.endAt(), status,
                homeScore, awayScore, bestOf, winner == null ? null : winner.toString(),
                Boolean.TRUE.equals(source.forfeit()), Boolean.TRUE.equals(source.draw()),
                status == EventStatus.LIVE && homeScore != null && awayScore != null));
    }

    public SportsTeam team(PandaScoreDtos.Team source) {
        if (source == null || source.id() == null || source.id() <= 0) return null;
        return new SportsTeam(source.id().toString(), clean(source.name()), clean(source.acronym()), safeLogo(source.imageUrl()));
    }

    public SportsChampionship championship(PandaScoreDtos.Tournament tournament,
            PandaScoreDtos.League matchLeague, PandaScoreDtos.Series matchSeries) {
        if (tournament == null || tournament.id() == null || tournament.id() <= 0) return null;
        var league = matchLeague != null ? matchLeague : tournament.league();
        var series = matchSeries != null ? matchSeries : tournament.serie();
        String leagueName = league == null ? null : clean(league.name());
        String seriesName = series == null ? null : first(series.fullName(), series.name());
        String season = series == null ? null : first(series.season(), series.year() == null ? null : series.year().toString());
        return new SportsChampionship(tournament.id().toString(), first(seriesName, leagueName, tournament.name()),
                clean(tournament.slug()), season, league == null ? null : safeLogo(league.imageUrl()),
                leagueName, seriesName, tournament.beginAt(), tournament.endAt());
    }

    private static boolean isCounterStrike(PandaScoreDtos.Videogame value) {
        if (value == null) return false;
        return Long.valueOf(3).equals(value.id()) || "csgo".equals(value.slug()) || "cs2".equals(value.slug())
                || "Counter-Strike 2".equalsIgnoreCase(value.name()) || "Counter-Strike".equalsIgnoreCase(value.name())
                || "CS:GO".equalsIgnoreCase(value.name());
    }

    private static Integer score(List<PandaScoreDtos.Result> results, String teamId) {
        if (results == null) return null;
        // A duplicate remains ambiguous even if one occurrence is null or invalid.
        // Filtering invalid scores first could silently select another conflicting row.
        var values = results.stream().filter(item -> item != null && item.teamId() != null
                && item.teamId().toString().equals(teamId)).toList();
        if (values.size() != 1) return null;
        Integer score = values.getFirst().score();
        return score != null && score >= 0 ? score : null;
    }

    private static Long winnerId(PandaScoreDtos.Match source) {
        Long nested = source.winner() == null ? null : source.winner().id();
        Long winner = source.winnerId() != null ? source.winnerId() : nested;
        if (source.winnerId() != null && nested != null && !source.winnerId().equals(nested)) return null;
        return winner != null && winner > 0 ? winner : null;
    }

    private static String safeLogo(String value) {
        if (clean(value) == null) return null;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null ? value : null;
        } catch (IllegalArgumentException ex) { return null; }
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static String first(String... values) {
        for (String value : values) if (clean(value) != null) return clean(value);
        return null;
    }
}
