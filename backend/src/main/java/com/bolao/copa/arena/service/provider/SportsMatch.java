package com.bolao.copa.arena.service.provider;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import java.time.Instant;

/** Normalized provider data, independent of HTTP transport and persistence. */
public record SportsMatch(String externalId, String title, SportsTeam homeTeam, SportsTeam awayTeam,
        SportsChampionship championship, Instant scheduledAt, Instant endedAt, EventStatus status,
        Integer homeScore, Integer awayScore, Integer bestOf, String winnerExternalId,
        boolean forfeit, boolean draw, boolean liveScoreAvailable, String sportCode) {
    /** Existing CS fixtures retain their identity; new adapters must supply their sport explicitly. */
    public SportsMatch(String externalId, String title, SportsTeam homeTeam, SportsTeam awayTeam,
            SportsChampionship championship, Instant scheduledAt, Instant endedAt, EventStatus status,
            Integer homeScore, Integer awayScore, Integer bestOf, String winnerExternalId,
            boolean forfeit, boolean draw, boolean liveScoreAvailable) {
        this(externalId,title,homeTeam,awayTeam,championship,scheduledAt,endedAt,status,homeScore,awayScore,
                bestOf,winnerExternalId,forfeit,draw,liveScoreAvailable,"CS2");
    }
}
