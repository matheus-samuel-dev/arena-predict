package com.bolao.copa.arena.service.provider;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ApiFootballProvider extends DocumentedSportsProvider {
    public ApiFootballProvider(Environment env,ObjectMapper json) {
        super("API_FOOTBALL","API-FOOTBALL","FOOTBALL",new ProviderConfig(env,"API_FOOTBALL","API_FOOTBALL_KEY",
                "https://v3.football.api-sports.io",SportsHttpSettings.Authentication.HEADER),json);
    }
    @Override public Set<ProviderCapability> capabilities(String sport) { return Set.of(ProviderCapability.EVENTS,
            ProviderCapability.LIVE_STATUS,ProviderCapability.LIVE_SCORE,ProviderCapability.PARTICIPANTS,
            ProviderCapability.CHAMPIONSHIPS,ProviderCapability.RESULTS); }
    public static EventStatus status(String raw) {
        return switch(raw) {
            case "TBD","NS" -> EventStatus.SCHEDULED;
            case "1H","HT","2H","ET","BT","P","LIVE" -> EventStatus.LIVE;
            case "FT","AET","PEN" -> EventStatus.FINISHED;
            case "PST","SUSP","INT" -> EventStatus.POSTPONED;
            case "CANC","ABD","AWD","WO" -> EventStatus.CANCELLED;
            default -> throw invalid();
        };
    }
    @Override public SportsMatch map(JsonNode s) {
        JsonNode fixture=s.path("fixture"); String raw=required(fixture.path("status").path("short")); EventStatus status=status(raw);
        SportsTeam home=team(s.path("teams").path("home")),away=team(s.path("teams").path("away"));
        // Football contracts settle on regulation time, never on a penalty shootout or extra-time aggregate.
        JsonNode score=status==EventStatus.FINISHED?s.path("score").path("fulltime"):s.path("goals");
        Integer hs=number(score.path("home")),as=number(score.path("away"));
        // During extra time/penalties the regulation score is already the contractual result.
        if(Set.of("ET","BT","P").contains(raw)) { hs=number(s.path("score").path("fulltime").path("home")); as=number(s.path("score").path("fulltime").path("away")); }
        Map<String,String> data=new TreeMap<>();
        pair(data,"firstHalf",number(s.path("score").path("halftime").path("home")),number(s.path("score").path("halftime").path("away")));
        return new SportsMatch(required(fixture.path("id")),home.name()+" vs "+away.name(),home,away,league(s.path("league")),
                instant(fixture.path("date")),null,status,hs,as,null,winner(home,away,hs,as),false,hs!=null&&hs.equals(as),
                hs!=null&&as!=null,"FOOTBALL",data,List.of(),raw,text(fixture.path("status").path("elapsed")),
                switch(raw) {case "1H" -> "1º tempo";case "HT" -> "Intervalo";case "2H" -> "2º tempo";case "ET","BT" -> "Prorrogação";case "P" -> "Pênaltis";default -> null;},
                Set.of("score"),SportsMatch.EventFormatHint.HEAD_TO_HEAD);
    }
    private List<SportsMatch> onDate(Instant at) { return mapped(fetch("/fixtures",Map.of("date",date(at),"timezone","UTC"),"response")); }
    @Override public List<SportsMatch> runningMatches() { return filter(mapped(fetch("/fixtures",Map.of("live","all","timezone","UTC"),"response")),EventStatus.LIVE); }
    @Override public List<SportsMatch> upcomingMatches(Instant from,Instant to) {
        List<SportsMatch> result=new ArrayList<>(); // Conservative free-plan horizon: today + tomorrow.
        for(int i=0;i<2;i++) result.addAll(filter(onDate(from.plusSeconds(i*86400L)),EventStatus.SCHEDULED));
        return result.stream().filter(m->m.scheduledAt()!=null&&!m.scheduledAt().isBefore(from)&&!m.scheduledAt().isAfter(to)).toList();
    }
    @Override public List<SportsMatch> finishedMatches(Instant since) {
        return filter(onDate(clock.instant().minusSeconds(86400)),EventStatus.FINISHED);
    }
    @Override public Optional<SportsMatch> matchDetails(String id) {
        if(!id.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid external ID");
        return mapped(fetch("/fixtures",Map.of("id",id,"timezone","UTC"),"response")).stream().findFirst();
    }
}
