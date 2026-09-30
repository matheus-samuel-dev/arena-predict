package com.bolao.copa.arena.service.sync;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.SportCategory;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.provider.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reuses the teams and tournaments embedded in match responses; no per-match HTTP lookups. */
@Service
public class SportsCatalogSyncService {
    public record References(Map<String, Long> teams, Map<String, Long> championships) { }
    private final CompetitorRepository teams;
    private final ChampionshipRepository championships;
    private final SportRepository sports;
    private final SportsSyncStateStore state;
    public SportsCatalogSyncService(CompetitorRepository teams, ChampionshipRepository championships,
                                   SportRepository sports, SportsSyncStateStore state) {
        this.teams=teams; this.championships=championships; this.sports=sports; this.state=state;
    }
    @Transactional
    public References synchronize(String provider, List<SportsMatch> matches) {
        state.lock(provider); // Also serializes insert races across instances.
        Sport sport=sports.findByCodeIgnoreCase("CS2").orElseGet(() -> {
            Sport value=new Sport(); value.setCode("CS2"); value.setName("Counter-Strike 2");
            value.setCategory(SportCategory.ESPORTS); value.setIcon("crosshair"); value.setDisplayOrder(7);
            return sports.save(value);
        });
        Map<String,SportsTeam> incomingTeams=new LinkedHashMap<>();
        Map<String,SportsChampionship> incomingChampionships=new LinkedHashMap<>();
        for (SportsMatch match:matches) {
            for (SportsTeam team:Arrays.asList(match.homeTeam(),match.awayTeam()))
                if (team!=null && text(team.externalId()) && text(team.name())) incomingTeams.put(team.externalId(),team);
            var championship=match.championship();
            if (championship!=null && text(championship.externalId()) && text(championship.name()))
                incomingChampionships.put(championship.externalId(),championship);
        }
        Map<String,Competitor> knownTeams=teams.findByExternalProviderAndExternalIdIn(provider,incomingTeams.keySet()).stream()
                .collect(Collectors.toMap(Competitor::getExternalId,Function.identity()));
        Map<String,Long> teamIds=new HashMap<>();
        incomingTeams.forEach((id, source) -> {
            Competitor team=knownTeams.getOrDefault(id,new Competitor());
            team.setSport(sport); team.setExternalProvider(provider); team.setExternalId(id);
            // This is an internal key, never a fabricated team acronym.
            team.setCode(internalKey(provider,id,30)); team.setName(cut(source.name(),120));
            team.setAcronym(cut(source.acronym(),40)); team.setImageUrl(cut(source.logoUrl(),2048));
            teamIds.put(id,teams.save(team).getId());
        });
        Map<String,Championship> knownChampionships=championships.findByExternalProviderAndExternalIdIn(provider,incomingChampionships.keySet()).stream()
                .collect(Collectors.toMap(Championship::getExternalId,Function.identity()));
        Map<String,Long> championshipIds=new HashMap<>();
        incomingChampionships.forEach((id,source) -> {
            Championship value=knownChampionships.getOrDefault(id,new Championship());
            value.setSport(sport); value.setExternalProvider(provider); value.setExternalId(id);
            value.setSlug(internalKey(provider,id,120).toLowerCase(Locale.ROOT));
            value.setName(cut(source.name(),120)); value.setSeason(cut(source.season(),40));
            value.setImageUrl(cut(source.logoUrl(),2048)); value.setLeagueName(cut(source.leagueName(),180));
            value.setSeriesName(cut(source.seriesName(),180)); value.setStartsAt(source.startsAt()); value.setEndsAt(source.endsAt());
            championshipIds.put(id,championships.save(value).getId());
        });
        return new References(Map.copyOf(teamIds),Map.copyOf(championshipIds));
    }
    static boolean text(String value) { return value!=null && !value.isBlank(); }
    static String cut(String value,int length) { return value==null?null:value.substring(0,Math.min(value.length(),length)); }
    private static String internalKey(String provider,String id,int max) {
        String key=provider+"_"+id;
        if (key.length()>max) throw new IllegalArgumentException("Provider identity exceeds internal key limit");
        return key;
    }
}
