package com.bolao.copa.arena.service.provider.pandascore;

import java.util.Arrays;
import java.util.Optional;

/** Fixtures coverage only; a running match does not imply paid in-game statistics. */
public enum PandaScoreGame {
    CSGO("csgo", "CS2", "Counter-Strike 2", "crosshair"),
    LOL("lol", "LEAGUE_OF_LEGENDS", "League of Legends", "swords"),
    VALORANT("valorant", "VALORANT", "Valorant", "crosshair");

    private final String path, sportCode, displayName, icon;
    PandaScoreGame(String path, String sportCode, String displayName, String icon) {
        this.path=path; this.sportCode=sportCode; this.displayName=displayName; this.icon=icon;
    }
    public String path() { return path; }
    public String sportCode() { return sportCode; }
    public String displayName() { return displayName; }
    public String icon() { return icon; }
    public static PandaScoreGame fromPath(String path) {
        return Arrays.stream(values()).filter(game -> game.path.equals(path)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported PandaScore videogame"));
    }
    public static PandaScoreGame fromSport(String code) {
        return Arrays.stream(values()).filter(game -> game.sportCode.equals(com.bolao.copa.arena.service.provider.SportsProviderRegistry.canonicalSport(code))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported normalized sport"));
    }
    public static Optional<PandaScoreGame> from(PandaScoreDtos.Videogame value) {
        if (value == null) return Optional.empty();
        if (Long.valueOf(3).equals(value.id()) || "csgo".equals(value.slug()) || "cs2".equals(value.slug())
                || "Counter-Strike 2".equalsIgnoreCase(value.name()) || "Counter-Strike".equalsIgnoreCase(value.name())
                || "CS:GO".equalsIgnoreCase(value.name())) return Optional.of(CSGO);
        if ("lol".equals(value.slug()) || "league-of-legends".equals(value.slug())
                || "League of Legends".equalsIgnoreCase(value.name())) return Optional.of(LOL);
        if ("valorant".equals(value.slug()) || "Valorant".equalsIgnoreCase(value.name())) return Optional.of(VALORANT);
        return Optional.empty();
    }
}
