package com.bolao.copa.arena.service.provider;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import java.time.Instant;

/** Normalized provider data, independent of HTTP transport and persistence. */
public record SportsMatch(String externalId, String title, SportsTeam homeTeam, SportsTeam awayTeam,
        SportsChampionship championship, Instant scheduledAt, Instant endedAt, EventStatus status,
        Integer homeScore, Integer awayScore, Integer bestOf, String winnerExternalId,
        boolean forfeit, boolean draw, boolean liveScoreAvailable, String sportCode,
        java.util.Map<String,String> resultData, java.util.List<SportsParticipant> participants,
        String rawStatus,String clock,String period,java.util.Set<String> supportedMetrics,EventFormatHint formatHint) {
    public enum EventFormatHint { HEAD_TO_HEAD, INDIVIDUAL, RACE }
    public SportsMatch {
        sportCode=SportsProviderRegistry.canonicalSport(sportCode);
        resultData=resultData==null?java.util.Map.of():java.util.Map.copyOf(resultData);
        participants=participants==null?java.util.List.of():java.util.List.copyOf(participants);
        supportedMetrics=supportedMetrics==null?java.util.Set.of("score"):java.util.Set.copyOf(supportedMetrics);
        formatHint=formatHint==null?EventFormatHint.HEAD_TO_HEAD:formatHint;
    }
    public SportsMatch(String externalId,String title,SportsTeam homeTeam,SportsTeam awayTeam,SportsChampionship championship,
            Instant scheduledAt,Instant endedAt,EventStatus status,Integer homeScore,Integer awayScore,Integer bestOf,
            String winnerExternalId,boolean forfeit,boolean draw,boolean liveScoreAvailable,String sportCode) {
        this(externalId,title,homeTeam,awayTeam,championship,scheduledAt,endedAt,status,homeScore,awayScore,bestOf,
                winnerExternalId,forfeit,draw,liveScoreAvailable,sportCode,java.util.Map.of(),java.util.List.of(),
                status==null?null:status.name(),null,null,java.util.Set.of("score"),EventFormatHint.HEAD_TO_HEAD);
    }
    /** Existing CS fixtures retain their identity; new adapters must supply their sport explicitly. */
    public SportsMatch(String externalId, String title, SportsTeam homeTeam, SportsTeam awayTeam,
            SportsChampionship championship, Instant scheduledAt, Instant endedAt, EventStatus status,
            Integer homeScore, Integer awayScore, Integer bestOf, String winnerExternalId,
            boolean forfeit, boolean draw, boolean liveScoreAvailable) {
        this(externalId,title,homeTeam,awayTeam,championship,scheduledAt,endedAt,status,homeScore,awayScore,
                bestOf,winnerExternalId,forfeit,draw,liveScoreAvailable,"CS2");
    }
}
