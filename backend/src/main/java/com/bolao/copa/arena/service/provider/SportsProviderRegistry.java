package com.bolao.copa.arena.service.provider;

import java.util.*;
import org.springframework.stereotype.Component;

/** One identity per adapter; availability never causes an event to switch external owner. */
@Component
public class SportsProviderRegistry {
    private final List<SportsDataProvider> providers;
    public SportsProviderRegistry(List<SportsDataProvider> providers) {
        this.providers=providers.stream().filter(p -> !p.demo()).sorted(Comparator.comparingInt(SportsDataProvider::priority).reversed()
                .thenComparing(SportsDataProvider::providerId)).toList();
        Set<String> ids=new HashSet<>();
        for(var provider:this.providers) if(!ids.add(provider.providerId())) throw new IllegalArgumentException("Duplicate provider identity");
    }
    public List<SportsDataProvider> providers() { return providers; }
    public SportsDataProvider byId(String id) {
        return providers.stream().filter(p -> p.providerId().equalsIgnoreCase(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider"));
    }
    public Optional<SportsDataProvider> getProvider(String sport) {
        String code=canonicalSport(sport);
        var eligible=providers.stream().filter(p -> p.supportedSports().stream().map(SportsProviderRegistry::canonicalSport).anyMatch(code::equals)).toList();
        return eligible.stream().filter(p -> p.enabled() && p.available()).findFirst().or(() -> eligible.stream().findFirst());
    }
    public static String canonicalSport(String code) {
        if(code==null) throw new IllegalArgumentException("Sport is required");
        return switch(code.toUpperCase(Locale.ROOT)) { case "LOL" -> "LEAGUE_OF_LEGENDS"; case "F1" -> "MOTORSPORT"; default -> code.toUpperCase(Locale.ROOT); };
    }
}
