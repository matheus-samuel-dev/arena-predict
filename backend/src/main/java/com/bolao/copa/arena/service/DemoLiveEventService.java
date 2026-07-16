package com.bolao.copa.arena.service;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.repository.ArenaEventRepository;
import com.bolao.copa.arena.service.provider.SportsDataProvider;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoLiveEventService {
    private final ArenaEventRepository events;
    private final List<SportsDataProvider> providers;
    public DemoLiveEventService(ArenaEventRepository events, ObjectProvider<SportsDataProvider> providers) {
        this.events = events; this.providers = providers.orderedStream().toList();
    }
    @Transactional
    public Map<String, Object> refresh() {
        int updated = 0;
        for (SportsDataProvider provider : providers) {
            for (var update : provider.liveUpdates()) {
                var event = events.findByExternalKeyForUpdate(update.externalKey()).orElse(null);
                if (event == null || !event.isDemo()) continue;
                event.setHomeScore(update.homeScore()); event.setAwayScore(update.awayScore());
                event.setClock(update.clock()); event.setPeriod(update.period()); event.setLiveData(update.structuredData());
                event.setStatus(EventStatus.LIVE); updated++;
            }
        }
        return Map.of("updated", updated, "demo", true, "providers", providers.stream().map(SportsDataProvider::providerName).toList());
    }

}
