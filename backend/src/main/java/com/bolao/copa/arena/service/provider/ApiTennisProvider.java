package com.bolao.copa.arena.service.provider;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ApiTennisProvider extends DocumentedSportsProvider {
    public ApiTennisProvider(Environment env,ObjectMapper json) {
        super("API_TENNIS","API-Tennis","TENNIS",new ProviderConfig(env,"API_TENNIS","API_TENNIS_KEY",
                "https://api.api-tennis.com",SportsHttpSettings.Authentication.FORM),json);
    }
    @Override public Set<ProviderCapability> capabilities(String sport) { return Set.of(ProviderCapability.EVENTS,
            ProviderCapability.LIVE_STATUS,ProviderCapability.LIVE_SCORE,ProviderCapability.PARTICIPANTS,
            ProviderCapability.CHAMPIONSHIPS,ProviderCapability.RESULTS,ProviderCapability.SETS,ProviderCapability.GAMES); }
    public static EventStatus status(String raw,boolean live) {
        String normalized=raw==null?"":raw.trim().toLowerCase(Locale.ROOT);
        if(Set.of("finished","ended").contains(normalized)) return EventStatus.FINISHED;
        if(Set.of("cancelled","canceled","retired","walkover","abandoned").contains(normalized)) return EventStatus.CANCELLED;
        if(Set.of("postponed","interrupted","suspended").contains(normalized)) return EventStatus.POSTPONED;
        if(live || normalized.matches("set [1-5]")) return EventStatus.LIVE;
        if(normalized.isEmpty()||Set.of("not started","scheduled").contains(normalized)) return EventStatus.SCHEDULED;
        throw invalid();
    }
    @Override public SportsMatch map(JsonNode s) {
        String raw=text(s.path("event_status")); EventStatus status=status(raw,"1".equals(text(s.path("event_live"))));
        // Doubles participant identity is not documented as a stable pair ID; do not invent one.
        if(!required(s.path("event_type_type")).toLowerCase(Locale.ROOT).contains("singles")) throw invalid();
        SportsTeam home=new SportsTeam(required(s.path("first_player_key")),required(s.path("event_first_player")),null,logo(text(s.path("event_first_player_logo"))));
        SportsTeam away=new SportsTeam(required(s.path("second_player_key")),required(s.path("event_second_player")),null,logo(text(s.path("event_second_player_logo"))));
        Integer h=null,a=null; String result=text(s.path("event_final_result"));
        if(result!=null && result.matches("\\s*[0-3]\\s*-\\s*[0-3]\\s*")) { String[] parts=result.trim().split("\\s*-\\s*");h=Integer.valueOf(parts[0]);a=Integer.valueOf(parts[1]); }
        Map<String,String> data=new TreeMap<>();
        for(JsonNode set:s.path("scores")) {
            Integer index=number(set.path("score_set")); if(index==null||index<1||index>5) throw invalid();
            pair(data,"set"+index,number(set.path("score_first")),number(set.path("score_second")));
        }
        // Aggregate games only when the final payload includes every completed set.
        if(status==EventStatus.FINISHED && h!=null&&a!=null&&s.path("scores").size()==h+a) {
            int hg=0,ag=0;boolean complete=true;
            for(int i=1;i<=h+a;i++) { if(!data.containsKey("set"+i+"Home")){complete=false;break;} hg+=Integer.parseInt(data.get("set"+i+"Home"));ag+=Integer.parseInt(data.get("set"+i+"Away")); }
            if(complete) pair(data,"games",hg,ag);
        }
        String reported=text(s.path("event_winner")); String winning="First Player".equals(reported)?home.externalId():"Second Player".equals(reported)?away.externalId():null;
        Instant starts;
        try { starts=LocalDateTime.parse(required(s.path("event_date"))+"T"+required(s.path("event_time"))).toInstant(ZoneOffset.UTC); }
        catch(DateTimeException ex) { throw invalid(); }
        return new SportsMatch(required(s.path("event_key")),home.name()+" vs "+away.name(),home,away,
                new SportsChampionship(required(s.path("tournament_key")),required(s.path("tournament_name")),null,text(s.path("tournament_season")),null,
                        text(s.path("tournament_name")),text(s.path("event_type_type")),null,null),starts,null,status,h,a,null,winning,false,false,
                h!=null&&a!=null,"TENNIS",data,List.of(),raw,null,raw!=null&&raw.toLowerCase(Locale.ROOT).matches("set [1-5]")?raw.substring(raw.length()-1)+"º set":null,
                Set.of("score"),SportsMatch.EventFormatHint.INDIVIDUAL);
    }
    private List<SportsMatch> read(Map<String,String> query) {
        // Request all, retain documented stable singles identities. Unsupported doubles remain outside this adapter.
        JsonNode rows=fetch("/tennis/",query,"result"); List<SportsMatch> result=new ArrayList<>();
        for(JsonNode row:rows) if(row.path("event_type_type").asText().toLowerCase(Locale.ROOT).contains("singles")) result.add(map(row));
        return List.copyOf(result);
    }
    @Override public List<SportsMatch> runningMatches() { return filter(read(Map.of("method","get_livescore","timezone","UTC")),EventStatus.LIVE); }
    @Override public List<SportsMatch> upcomingMatches(Instant from,Instant to) {
        return filter(read(Map.of("method","get_fixtures","date_start",date(from),"date_stop",date(to),"timezone","UTC")),EventStatus.SCHEDULED);
    }
    @Override public List<SportsMatch> finishedMatches(Instant since) {
        return filter(read(Map.of("method","get_fixtures","date_start",date(since),"date_stop",date(clock.instant()),"timezone","UTC")),EventStatus.FINISHED);
    }
    @Override public Optional<SportsMatch> matchDetails(String id) {
        if(!id.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid external ID");
        return read(Map.of("method","get_fixtures","match_key",id,"date_start",date(clock.instant().minusSeconds(3*86400)),
                "date_stop",date(clock.instant().plusSeconds(7*86400)),"timezone","UTC")).stream().findFirst();
    }
}
