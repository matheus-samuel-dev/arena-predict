package com.bolao.copa.arena.config;

import com.bolao.copa.arena.service.DemoLiveEventService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = {"app.demo.enabled", "app.demo.live-scheduler-enabled"}, havingValue = "true")
public class DemoLiveRefreshScheduler {
    private final DemoLiveEventService liveEvents;
    public DemoLiveRefreshScheduler(DemoLiveEventService liveEvents) { this.liveEvents = liveEvents; }

    @Scheduled(fixedDelayString = "${app.demo.live-refresh-ms:30000}", initialDelayString = "${app.demo.live-initial-delay-ms:30000}")
    public void refresh() { liveEvents.refresh(); }
}
