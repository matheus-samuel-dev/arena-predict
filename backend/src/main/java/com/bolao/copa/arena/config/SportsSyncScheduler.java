package com.bolao.copa.arena.config;

import com.bolao.copa.arena.service.sync.MultiProviderSyncCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="sports.sync.scheduler-enabled",havingValue="true",matchIfMissing=true)
public class SportsSyncScheduler {
    private final MultiProviderSyncCoordinator sync;
    public SportsSyncScheduler(MultiProviderSyncCoordinator sync) { this.sync=sync; }
    @Scheduled(fixedDelayString="${sports.sync.interval-ms:30000}",initialDelayString="${sports.sync.initial-delay-ms:15000}")
    public void synchronize() { sync.scheduledSynchronize(); }
}
