package com.bolao.copa.arena.service.provider;

import java.util.List;

public interface SportsDataProvider {
    String providerName();
    boolean demo();
    List<LiveEventUpdate> liveUpdates();

    record LiveEventUpdate(String externalKey, Integer homeScore, Integer awayScore,
                           String clock, String period, String structuredData) { }
}
