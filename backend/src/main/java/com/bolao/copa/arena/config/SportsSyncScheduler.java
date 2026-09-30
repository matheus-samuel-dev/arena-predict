package com.bolao.copa.arena.config;

import com.bolao.copa.arena.service.sync.SportsSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="sports.sync.enabled",havingValue="true")
public class SportsSyncScheduler {
    private final SportsSyncService sync;
    public SportsSyncScheduler(SportsSyncService sync) { this.sync=sync; }
    @Scheduled(fixedDelayString="${sports.sync.interval-ms:30000}",initialDelayString="${sports.sync.initial-delay-ms:15000}")
    public void synchronize() { sync.synchronize(); }
}
