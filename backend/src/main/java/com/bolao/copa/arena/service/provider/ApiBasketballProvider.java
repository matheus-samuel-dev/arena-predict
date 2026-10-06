package com.bolao.copa.arena.service.provider;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ApiBasketballProvider extends DocumentedSportsProvider {
    public ApiBasketballProvider(Environment env,ObjectMapper json) {
        super("API_BASKETBALL","API-BASKETBALL","BASKETBALL",new ProviderConfig(env,"API_BASKETBALL","API_BASKETBALL_KEY",
                "https://v1.basketball.api-sports.io",SportsHttpSettings.Authentication.HEADER),json);
    }
    @Override public Set<ProviderCapability> capabilities(String sport) { return Set.of(ProviderCapability.EVENTS,
            ProviderCapability.LIVE_STATUS,ProviderCapability.LIVE_SCORE,ProviderCapability.PARTICIPANTS,
            ProviderCapability.CHAMPIONSHIPS,ProviderCapability.RESULTS,ProviderCapability.PERIODS); }
    public static EventStatus status(String raw) {
        return switch(raw) {
            case "NS" -> EventStatus.SCHEDULED;
            case "Q1","Q2","Q3","Q4","OT","BT","HT" -> EventStatus.LIVE;
            case "FT","AOT" -> EventStatus.FINISHED;
            case "POST","SUSP" -> EventStatus.POSTPONED;
            case "CANC","AWD","ABD" -> EventStatus.CANCELLED;
            default -> throw invalid();
        };
    }
    @Override public SportsMatch map(JsonNode s) {
        String raw=required(s.path("status").path("short")); EventStatus status=status(raw);
        SportsTeam home=team(s.path("teams").path("home")),away=team(s.path("teams").path("away"));
        JsonNode hs=s.path("scores").path("home"),as=s.path("scores").path("away");
        Integer h=number(hs.path("total")),a=number(as.path("total")); Map<String,String> data=new TreeMap<>();
        for(int q=1;q<=4;q++) pair(data,"quarter"+q,number(hs.path("quarter_"+q)),number(as.path("quarter_"+q)));
        if(data.containsKey("quarter1Home")&&data.containsKey("quarter2Home"))
            pair(data,"firstHalf",number(hs.path("quarter_1"))+number(hs.path("quarter_2")),number(as.path("quarter_1"))+number(as.path("quarter_2")));
        return new SportsMatch(required(s.path("id")),home.name()+" vs "+away.name(),home,away,league(s.path("league")),instant(s.path("date")),
                null,status,h,a,null,winner(home,away,h,a),false,h!=null&&h.equals(a),h!=null&&a!=null,"BASKETBALL",data,List.of(),raw,
                text(s.path("status").path("timer")),switch(raw) {case "Q1" -> "1º quarto";case "Q2" -> "2º quarto";case "Q3" -> "3º quarto";case "Q4" -> "4º quarto";case "OT" -> "Prorrogação";case "HT","BT" -> "Intervalo";default -> null;},
                Set.of("score"),SportsMatch.EventFormatHint.HEAD_TO_HEAD);
    }
    private List<SportsMatch> onDate(Instant at) { return mapped(fetch("/games",Map.of("date",date(at),"timezone","UTC"),"response")); }
    @Override public List<SportsMatch> runningMatches() {
        List<SportsMatch> result=new ArrayList<>(filter(onDate(clock.instant()),EventStatus.LIVE));
        result.addAll(filter(onDate(clock.instant().minusSeconds(86400)),EventStatus.LIVE)); // Cross-midnight games.
        return List.copyOf(result);
    }
    @Override public List<SportsMatch> upcomingMatches(Instant from,Instant to) {
        List<SportsMatch> result=new ArrayList<>();
        for(int i=0;i<2;i++) result.addAll(filter(onDate(from.plusSeconds(i*86400L)),EventStatus.SCHEDULED));
        return result.stream().filter(m->m.scheduledAt()!=null&&!m.scheduledAt().isBefore(from)&&!m.scheduledAt().isAfter(to)).toList();
    }
    @Override public List<SportsMatch> finishedMatches(Instant since) { return filter(onDate(clock.instant().minusSeconds(86400)),EventStatus.FINISHED); }
    @Override public Optional<SportsMatch> matchDetails(String id) {
        if(!id.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid external ID");
        return mapped(fetch("/games",Map.of("id",id,"timezone","UTC"),"response")).stream().findFirst();
    }
}
