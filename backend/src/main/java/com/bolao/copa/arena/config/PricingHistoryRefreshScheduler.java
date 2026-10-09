package com.bolao.copa.arena.config;

import com.bolao.copa.arena.service.HistoricalTeamStrengthService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reads only local confirmed results; never consumes provider quota or alters live sync cadence. */
@Component
@ConditionalOnProperty(name="sports.pricing.history-refresh-enabled",havingValue="true",matchIfMissing=true)
public class PricingHistoryRefreshScheduler {
    private final HistoricalTeamStrengthService history;
    public PricingHistoryRefreshScheduler(HistoricalTeamStrengthService history) { this.history=history; }
    @Scheduled(initialDelay=15000,fixedDelay=30000)
    public void refresh() { if(history.refreshDue())history.refresh(); }
}
