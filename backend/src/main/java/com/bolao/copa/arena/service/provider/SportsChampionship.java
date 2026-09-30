package com.bolao.copa.arena.service.provider;

import java.time.Instant;

public record SportsChampionship(String externalId, String name, String slug, String season,
        String logoUrl, String leagueName, String seriesName, Instant startsAt, Instant endsAt) { }
