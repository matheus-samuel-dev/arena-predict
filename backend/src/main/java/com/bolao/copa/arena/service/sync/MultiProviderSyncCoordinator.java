package com.bolao.copa.arena.service.sync;

import com.bolao.copa.arena.config.SportsSyncProperties;
import com.bolao.copa.arena.repository.ArenaEventRepository;
import com.bolao.copa.arena.service.provider.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Independent quotas and database leases. User requests never call these adapters. */
@Service
public class MultiProviderSyncCoordinator {
    private final SportsProviderRegistry registry;
    private final Map<String,SportsSyncService> workers=new LinkedHashMap<>();
    public MultiProviderSyncCoordinator(SportsProviderRegistry registry,SportsSyncService primary,SportsSyncProperties properties,
            SportsSyncStateStore state,SportsCatalogSyncService catalog,SportsMatchSyncService matches,ArenaEventRepository events) {
        this.registry=registry;
        for(var provider:registry.providers()) {
            if(provider.providerId().equals("PANDASCORE")&&provider.providerId().equalsIgnoreCase(properties.provider())) workers.put(provider.providerId(),primary);
            else if(provider.providerId().equals("PANDASCORE")) {
                var panda=new SportsSyncProperties("PANDASCORE",properties.enabled(),properties.upcomingIntervalMs(),properties.runningIntervalMs(),
                        properties.finishedIntervalMs(),properties.trackedIntervalMs(),properties.nearStartMinutes(),properties.upcomingDays(),
                        properties.correctionWindowHours(),properties.trackedBatchSize(),properties.leaseMs(),properties.intervalMs(),properties.initialDelayMs());
                workers.put(provider.providerId(),new SportsSyncService(List.of(provider),panda,state,catalog,matches,events));
            }
            else {
                long interval=provider.pollingIntervalMs();
                var settings=new SportsSyncProperties(provider.providerId(),provider.enabled(),Math.max(interval,21600000),interval,
                        Math.max(interval,21600000),interval,30,7,72,5,properties.leaseMs(),properties.intervalMs(),properties.initialDelayMs());
                workers.put(provider.providerId(),new SportsSyncService(List.of(provider),settings,state,catalog,matches,events));
            }
        }
    }
    public void scheduledSynchronize() { workers.values().forEach(SportsSyncService::scheduledSynchronize); }
    public SportsSyncService.Status defaultStatus() { return workers.get("PANDASCORE").status(); }
    public List<ProviderView> providers() {
        return registry.providers().stream().map(p->{var status=workers.get(p.providerId()).status();
            Map<String,Set<ProviderCapability>> capabilities=new TreeMap<>();
            p.supportedSports().forEach(s->capabilities.put(s,p.capabilities(s)));
            return new ProviderView(p.providerId(),p.credentialVariable(),!status.configured()?"READY_FOR_CREDENTIAL":status.status(),capabilities,status);
        }).toList();
    }
    public SportsSyncService.Summary summary() {
        var active=workers.values().stream().map(SportsSyncService::status).filter(SportsSyncService.Status::enabled).toList();
        boolean healthy=!active.isEmpty()&&active.stream().allMatch(s->s.configured()&&"ONLINE".equals(s.status())&&s.lastSuccessAt()!=null);
        var oldest=active.stream().map(SportsSyncService.Status::lastSuccessAt).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        return new SportsSyncService.Summary(healthy,healthy?oldest:null);
    }
    public record ProviderView(String id,String credentialVariable,String readiness,Map<String,Set<ProviderCapability>> capabilities,SportsSyncService.Status sync) { }
}
