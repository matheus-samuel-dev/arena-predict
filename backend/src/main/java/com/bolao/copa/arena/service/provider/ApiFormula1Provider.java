package com.bolao.copa.arena.service.provider;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ApiFormula1Provider extends DocumentedSportsProvider {
    public ApiFormula1Provider(Environment env,ObjectMapper json) {
        super("API_FORMULA1","API-FORMULA-1","MOTORSPORT",new ProviderConfig(env,"API_FORMULA1","API_FORMULA1_KEY",
                "https://v1.formula-1.api-sports.io",SportsHttpSettings.Authentication.HEADER),json);
    }
    @Override public Set<ProviderCapability> capabilities(String sport) { return Set.of(ProviderCapability.EVENTS,
            ProviderCapability.LIVE_STATUS,ProviderCapability.CHAMPIONSHIPS,ProviderCapability.RESULTS,ProviderCapability.CLASSIFICATION); }
    @Override public Set<String> resultMetrics(String sport) { return Set.of(); }
    public static EventStatus status(String raw) {
        return switch(raw) {
            case "Scheduled" -> EventStatus.SCHEDULED; case "Live" -> EventStatus.LIVE;
            case "Completed" -> EventStatus.FINISHED; case "Postponed" -> EventStatus.POSTPONED;
            case "Cancelled" -> EventStatus.CANCELLED; default -> throw invalid();
        };
    }
    @Override public SportsMatch map(JsonNode s) { return map(s,List.of()); }
    public SportsMatch map(JsonNode s,List<SportsParticipant> classification) {
        String raw=required(s.path("status")); EventStatus status=status(raw); String id=required(s.path("id"));
        JsonNode competition=s.path("competition"); String name=required(competition.path("name"));
        Integer lap=number(s.path("laps").path("current")),total=number(s.path("laps").path("total"));
        String period=lap==null?null:"Volta "+lap+(total==null?"":" de "+total);
        return new SportsMatch(id,name,null,null,new SportsChampionship(required(competition.path("id")),name,null,text(s.path("season")),
                null,"Fórmula 1",null,null,null),instant(s.path("date")),null,status,null,null,null,
                classification.stream().filter(p->Integer.valueOf(1).equals(p.position())).map(p->p.participant().externalId()).findFirst().orElse(null),
                false,false,false,"MOTORSPORT",Map.of(),classification,raw,null,period,Set.of(),SportsMatch.EventFormatHint.RACE);
    }
    public List<SportsParticipant> classification(JsonNode rows) {
        List<SportsParticipant> result=new ArrayList<>(); Set<String> ids=new HashSet<>(); Set<Integer> positions=new HashSet<>();
        for(JsonNode row:rows) {
            JsonNode driver=row.path("driver"); String id=required(driver.path("id")); Integer position=number(row.path("position"));
            if(position!=null&&position==0) position=null; // DNF is not a fabricated finishing position.
            if(!ids.add(id)||(position!=null&&!positions.add(position))) throw invalid();
            result.add(new SportsParticipant(new SportsTeam(id,required(driver.path("name")),text(driver.path("abbr")),logo(text(driver.path("image")))),
                    position,text(row.path("time"))));
        }
        return List.copyOf(result);
    }
    private List<SportsMatch> races(Map<String,String> query) {
        List<SportsMatch> result=new ArrayList<>();
        for(JsonNode row:fetch("/races",query,"response")) {
            if(!"Race".equals(row.path("type").asText())) continue;
            List<SportsParticipant> participants="Completed".equals(row.path("status").asText())?
                    classification(fetch("/rankings/races",Map.of("race",required(row.path("id"))),"response")):List.of();
            result.add(map(row,participants));
        }
        return List.copyOf(result);
    }
    @Override public List<SportsMatch> runningMatches() { return filter(races(Map.of("date",date(clock.instant()),"type","Race","timezone","UTC")),EventStatus.LIVE); }
    @Override public List<SportsMatch> upcomingMatches(Instant from,Instant to) { return filter(races(Map.of("next","2","type","Race","timezone","UTC")),EventStatus.SCHEDULED); }
    @Override public List<SportsMatch> finishedMatches(Instant since) { return filter(races(Map.of("last","1","type","Race","timezone","UTC")),EventStatus.FINISHED); }
    @Override public Optional<SportsMatch> matchDetails(String id) {
        if(!id.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid external ID");
        return races(Map.of("id",id,"timezone","UTC")).stream().findFirst();
    }
}
