package com.bolao.copa.arena.service.provider;

import java.util.regex.Pattern;

/** Resolves provider edition labels without losing the competition's identity. */
public final class ChampionshipNames {
    private static final Pattern EDITION = Pattern.compile(
            "(?i)^(?:\\d{4}|(?:season|regular season|closed qualifier|open qualifier|series)\\b.*)$");

    private ChampionshipNames() { }

    public static String resolve(String name, String league, String series) {
        String edition = clean(series);
        String competition = clean(league);
        String fallback = clean(name);
        String candidate = edition != null ? edition : competition != null ? competition : fallback;
        if (candidate != null && EDITION.matcher(candidate).matches() && competition != null) {
            return competition + " · " + candidate;
        }
        return candidate != null ? candidate : competition;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
