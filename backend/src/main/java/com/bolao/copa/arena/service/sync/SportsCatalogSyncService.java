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
        Map<String,Sport> sportByCode=new HashMap<>();
        Map<String,SportsTeam> incomingTeams=new LinkedHashMap<>();
        Map<String,SportsChampionship> incomingChampionships=new LinkedHashMap<>();
        Map<String,Sport> teamSports=new HashMap<>(), championshipSports=new HashMap<>();
        for (SportsMatch match:matches) {
            Sport sport=sportByCode.computeIfAbsent(match.sportCode(),this::sport);
            for (SportsTeam team:Arrays.asList(match.homeTeam(),match.awayTeam()))
                if (team!=null && text(team.externalId()) && text(team.name())) {
                    requireSameSport(teamSports.putIfAbsent(team.externalId(),sport),sport);
                    incomingTeams.put(team.externalId(),team);
                }
            var championship=match.championship();
            if (championship!=null && text(championship.externalId()) && text(championship.name())) {
                requireSameSport(championshipSports.putIfAbsent(championship.externalId(),sport),sport);
                incomingChampionships.put(championship.externalId(),championship);
            }
        }
        Map<String,Competitor> knownTeams=teams.findByExternalProviderAndExternalIdIn(provider,incomingTeams.keySet()).stream()
                .collect(Collectors.toMap(Competitor::getExternalId,Function.identity()));
        Map<String,Long> teamIds=new HashMap<>();
        incomingTeams.forEach((id, source) -> {
            Competitor team=knownTeams.getOrDefault(id,new Competitor());
            Sport sport=teamSports.get(id);
            requireSameSport(team.getSport(),sport);
            team.setSport(sport); team.setExternalProvider(provider); team.setExternalId(id);
            // This is an internal key, never a fabricated team acronym.
            team.setCode(internalKey(provider,id,30)); team.setName(cut(source.name(),120));
            if (text(source.acronym())) team.setAcronym(cut(source.acronym(),40));
            if (replaceLogo(team.getImageUrl(),source.logoUrl())) team.setImageUrl(cut(source.logoUrl(),2048));
            teamIds.put(id,teams.save(team).getId());
        });
        Map<String,Championship> knownChampionships=championships.findByExternalProviderAndExternalIdIn(provider,incomingChampionships.keySet()).stream()
                .collect(Collectors.toMap(Championship::getExternalId,Function.identity()));
        Map<String,Long> championshipIds=new HashMap<>();
        incomingChampionships.forEach((id,source) -> {
            Championship value=knownChampionships.getOrDefault(id,new Championship());
            Sport sport=championshipSports.get(id);
            requireSameSport(value.getSport(),sport);
            value.setSport(sport); value.setExternalProvider(provider); value.setExternalId(id);
            value.setSlug(internalKey(provider,id,120).toLowerCase(Locale.ROOT));
            value.setName(cut(source.name(),120)); value.setSeason(cut(source.season(),40));
            if (replaceLogo(value.getImageUrl(),source.logoUrl())) value.setImageUrl(cut(source.logoUrl(),2048));
            value.setLeagueName(cut(source.leagueName(),180));
            value.setSeriesName(cut(source.seriesName(),180)); value.setStartsAt(source.startsAt()); value.setEndsAt(source.endsAt());
            championshipIds.put(id,championships.save(value).getId());
        });
        return new References(Map.copyOf(teamIds),Map.copyOf(championshipIds));
    }
    private Sport sport(String code) {
        if (!List.of("CS2","LOL","VALORANT").contains(code))
            throw new IllegalArgumentException("Unsupported normalized esports sport");
        return sports.findByCodeIgnoreCase(code).orElseGet(() -> {
            Sport value=new Sport(); value.setCode(code);
            value.setName(switch(code) { case "CS2" -> "Counter-Strike 2"; case "LOL" -> "League of Legends"; default -> "Valorant"; });
            value.setCategory(SportCategory.ESPORTS); value.setIcon("LOL".equals(code)?"swords":"crosshair");
            value.setDisplayOrder("CS2".equals(code)?7:"VALORANT".equals(code)?8:9);
            return sports.save(value);
        });
    }
    private static void requireSameSport(Sport existing,Sport incoming) {
        if (existing!=null && !existing.getCode().equals(incoming.getCode()))
            throw new IllegalArgumentException("Provider catalog identity cannot change sport");
    }
    private static boolean replaceLogo(String current,String incoming) {
        return text(incoming) && (current==null || !current.startsWith("/assets/"));
    }
    static boolean text(String value) { return value!=null && !value.isBlank(); }
    static String cut(String value,int length) { return value==null?null:value.substring(0,Math.min(value.length(),length)); }
    private static String internalKey(String provider,String id,int max) {
        String key=provider+"_"+id;
        if (key.length()>max) throw new IllegalArgumentException("Provider identity exceeds internal key limit");
        return key;
    }
}
